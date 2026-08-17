package com.center.google.client;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Thin Google People API client for the Contacts sync: find a contact by phone,
 * create a contact, and rename an existing one. Stateless - the caller passes a
 * valid access token per call. All failures are surfaced as {@link RestClientException}.
 */
@Component
@Slf4j
public class GooglePeopleClient {

    private static final String BASE = "https://people.googleapis.com/v1/";
    /** How long one token's searchContacts warmup counts as done. */
    private static final Duration WARMUP_TTL = Duration.ofMinutes(30);
    private static final int MAX_WARMUP_ENTRIES = 50;

    private final RestClient rest;
    private final ConcurrentHashMap<String, Instant> warmedUp = new ConcurrentHashMap<>();

    public GooglePeopleClient(RestClient rest) {
        this.rest = rest;
    }

    /** A Google contact reference (resourceName + current etag). */
    public record PersonRef(String resourceName, String etag) {}

    /** A contact as the address book lists it: who it is, and which numbers it holds. */
    public record Contact(String resourceName, String etag, String name, List<String> phones) {}

    /**
     * Every contact in the account, names and phone numbers, one page at a time.
     *
     * <p>This exists so a full check does not have to ASK about each number. One
     * search per phone is one "critical read" apiece and a roster of a few
     * hundred spends Google's whole per-minute quota in seconds; the whole
     * address book is a thousand contacts per read, and it answers the real
     * question - what does Google currently hold, and under what name - which a
     * per-phone search cannot (it says nothing about the name).
     */
    @SuppressWarnings("unchecked")
    public List<Contact> listContacts(String accessToken) {
        List<Contact> out = new ArrayList<>();
        String pageToken = null;
        do {
            String url = UriComponentsBuilder.fromUriString(BASE + "people/me/connections")
                    .queryParam("personFields", "names,phoneNumbers")
                    .queryParam("pageSize", 1000)
                    .queryParamIfPresent("pageToken", Optional.ofNullable(pageToken))
                    .build().toUriString();
            Map<String, Object> res;
            try {
                res = rest.get().uri(url)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve().body(Map.class);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                throw rateLimit(ex);
            }
            if (res == null) {
                break;
            }
            List<Map<String, Object>> people = (List<Map<String, Object>>) res.get("connections");
            for (Map<String, Object> person : people == null ? List.<Map<String, Object>>of() : people) {
                List<Map<String, Object>> names = (List<Map<String, Object>>) person.get("names");
                List<Map<String, Object>> phones = (List<Map<String, Object>>) person.get("phoneNumbers");
                List<String> numbers = new ArrayList<>();
                for (Map<String, Object> p : phones == null ? List.<Map<String, Object>>of() : phones) {
                    numbers.add(String.valueOf(p.getOrDefault("value", "")));
                }
                if (numbers.isEmpty()) {
                    continue; // nothing to match a student's number against
                }
                // unstructuredName is the string as it was WRITTEN; displayName is
                // Google's rebuild of it from the parts it parsed. Prefer the
                // former: comparing against a rebuilt name is what makes an
                // already-correct contact look wrong.
                String name = "";
                if (names != null && !names.isEmpty()) {
                    Object raw = names.get(0).get("unstructuredName");
                    name = String.valueOf(raw != null ? raw : names.get(0).getOrDefault("displayName", ""));
                }
                out.add(new Contact(String.valueOf(person.get("resourceName")),
                        String.valueOf(person.get("etag")), name, numbers));
            }
            Object next = res.get("nextPageToken");
            pageToken = next == null ? null : String.valueOf(next);
        } while (pageToken != null && !pageToken.isBlank());
        return out;
    }

    /** The last 9 digits of a phone - the form both sides are compared on. */
    public static String phoneKey(String phone) {
        return tail(phone);
    }

    /**
     * Find an existing contact whose phone matches {@code phone} (compared by the
     * last 9 significant digits, so country-code / formatting differences match).
     */
    @SuppressWarnings("unchecked")
    public Optional<PersonRef> findByPhone(String accessToken, String phone) {
        String needle = tail(phone);
        if (needle.isEmpty()) return Optional.empty();
        warmup(accessToken);
        try {
            String url = UriComponentsBuilder.fromUriString(BASE + "people:searchContacts")
                    .queryParam("query", needle)
                    .queryParam("readMask", "phoneNumbers")
                    .queryParam("pageSize", 10)
                    .build().toUriString();
            Map<String, Object> res = rest.get().uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(Map.class);
            List<Map<String, Object>> results = res == null ? null : (List<Map<String, Object>>) res.get("results");
            if (results == null) return Optional.empty();
            for (Map<String, Object> r : results) {
                Map<String, Object> person = (Map<String, Object>) r.get("person");
                if (person == null) continue;
                List<Map<String, Object>> phones = (List<Map<String, Object>>) person.get("phoneNumbers");
                if (phones == null) continue;
                for (Map<String, Object> p : phones) {
                    String value = String.valueOf(p.getOrDefault("value", ""));
                    if (tail(value).equals(needle)) {
                        return Optional.of(new PersonRef(
                                String.valueOf(person.get("resourceName")),
                                String.valueOf(person.get("etag"))));
                    }
                }
            }
            return Optional.empty();
        } catch (HttpClientErrorException.TooManyRequests ex) {
            // Not "no match": the question was never answered. Swallowing it here
            // would have the caller create a SECOND contact for a number Google
            // already holds, which is the one mistake this lookup exists to stop.
            throw rateLimit(ex);
        } catch (RestClientException ex) {
            log.warn("Google searchContacts failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * searchContacts needs one warmup call (empty query) before it will answer a
     * real one - but only ONCE per token, not once per lookup.
     *
     * <p>It used to run before every single search, which doubled the number of
     * "critical read" requests a sync spends and was half of what pushed a full
     * roster over Google's 90-reads-per-minute quota.
     */
    private void warmup(String accessToken) {
        Instant last = warmedUp.get(accessToken);
        if (last != null && last.isAfter(Instant.now().minus(WARMUP_TTL))) {
            return;
        }
        try {
            rest.get().uri(BASE + "people:searchContacts?query=&readMask=phoneNumbers")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
            // Tokens rotate about hourly, so the map would otherwise grow forever.
            if (warmedUp.size() > MAX_WARMUP_ENTRIES) {
                warmedUp.clear();
            }
            warmedUp.put(accessToken, Instant.now());
        } catch (RestClientException ignored) {
            // Warmup is best-effort; a failure is not recorded, so it is retried.
        }
    }

    /** Google's own advice on when to come back, or a whole minute if it said nothing. */
    private static GoogleRateLimitException rateLimit(HttpClientErrorException ex) {
        String header = ex.getResponseHeaders() == null ? null
                : ex.getResponseHeaders().getFirst("Retry-After");
        int wait = 60;
        if (header != null) {
            try {
                wait = Integer.parseInt(header.trim());
            } catch (NumberFormatException ignored) {
                // A date-formatted Retry-After: the default minute is close enough.
            }
        }
        return new GoogleRateLimitException(wait, "تم بلوغ حد Google المسموح به مؤقتاً");
    }

    /** Create a new contact with a display name and a phone number. */
    @SuppressWarnings("unchecked")
    public PersonRef createContact(String accessToken, String name, String phone) {
        Map<String, Object> body = Map.of(
                "names", List.of(Map.of("unstructuredName", name)),
                "phoneNumbers", List.of(Map.of("value", phone)));
        Map<String, Object> res = rest.post().uri(BASE + "people:createContact")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Map.class);
        return new PersonRef(String.valueOf(res.get("resourceName")), String.valueOf(res.get("etag")));
    }

    /**
     * Rename an existing contact. The etag must be current; if it is stale Google
     * rejects the update, so we re-fetch the etag once and retry.
     */
    public PersonRef renameContact(String accessToken, String resourceName, String etag, String name) {
        try {
            return patchName(accessToken, resourceName, etag, name);
        } catch (RestClientException ex) {
            PersonRef fresh = get(accessToken, resourceName);
            if (fresh == null) throw ex;
            return patchName(accessToken, resourceName, fresh.etag(), name);
        }
    }

    @SuppressWarnings("unchecked")
    private PersonRef patchName(String accessToken, String resourceName, String etag, String name) {
        String url = resourceName + ":updateContact?updatePersonFields=names";
        Map<String, Object> body = Map.of(
                "etag", etag,
                "names", List.of(Map.of("unstructuredName", name)));
        Map<String, Object> res = rest.patch().uri(BASE + url)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Map.class);
        return new PersonRef(String.valueOf(res.get("resourceName")), String.valueOf(res.get("etag")));
    }

    @SuppressWarnings("unchecked")
    public PersonRef get(String accessToken, String resourceName) {
        try {
            Map<String, Object> res = rest.get().uri(BASE + resourceName + "?personFields=names")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(Map.class);
            return res == null ? null
                    : new PersonRef(String.valueOf(res.get("resourceName")), String.valueOf(res.get("etag")));
        } catch (RestClientException ex) {
            log.warn("Google get person failed: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Remove a contact. A contact already gone (404) counts as success - the
     * requested end state holds either way, and the reconciler must be safe to
     * run again over the same rows.
     */
    public void deleteContact(String accessToken, String resourceName) {
        try {
            rest.delete().uri(BASE + resourceName + ":deleteContact")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ignored) {
            log.debug("Google contact {} was already gone", resourceName);
        }
    }

    /** The last 9 digits of a phone, ignoring spaces/plus/country code. */
    private static String tail(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        return digits.length() <= 9 ? digits : digits.substring(digits.length() - 9);
    }
}
