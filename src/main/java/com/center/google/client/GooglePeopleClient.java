package com.center.google.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

    private final RestClient rest = RestClient.create();

    /** A Google contact reference (resourceName + current etag). */
    public record PersonRef(String resourceName, String etag) {}

    /**
     * Find an existing contact whose phone matches {@code phone} (compared by the
     * last 9 significant digits, so country-code / formatting differences match).
     */
    @SuppressWarnings("unchecked")
    public Optional<PersonRef> findByPhone(String accessToken, String phone) {
        String needle = tail(phone);
        if (needle.isEmpty()) return Optional.empty();
        // searchContacts needs a warmup (empty query) before the first real query.
        try {
            rest.get().uri(BASE + "people:searchContacts?query=&readMask=phoneNumbers")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException ignored) {
            // Warmup is best-effort.
        }
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
        } catch (RestClientException ex) {
            log.warn("Google searchContacts failed: {}", ex.getMessage());
            return Optional.empty();
        }
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

    /** The last 9 digits of a phone, ignoring spaces/plus/country code. */
    private static String tail(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        return digits.length() <= 9 ? digits : digits.substring(digits.length() - 9);
    }
}
