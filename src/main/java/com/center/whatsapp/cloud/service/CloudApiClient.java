package com.center.whatsapp.cloud.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.center.common.config.ApplicationProperties;
import com.center.common.exception.BusinessRuleException;
import com.center.whatsapp.service.WaPhone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Every call this system makes to Meta's WhatsApp Cloud API - sending, uploading,
 * provisioning numbers, reading templates.
 *
 * <p>Two rules shape the whole class:
 *
 * <ol>
 *   <li><b>One token, many numbers.</b> Meta authenticates the business, not the
 *       number, so there is no per-number credential to resolve: the token and
 *       the WhatsApp Business Account come from the environment, and a number is
 *       addressed by its {@code phone_number_id}.</li>
 *   <li><b>Business-initiated means template.</b> Free-form text reaches a person
 *       only inside the 24 hours after their own last message. Every other send
 *       must name an approved template, which is why {@link #sendText} exists
 *       mainly to answer replies and {@link #sendTemplate} is the normal path.</li>
 * </ol>
 *
 * <p>Failures are returned, not thrown, on the send paths: a batch of parent
 * messages must record each outcome and carry on, exactly as the messaging path
 * does. Provisioning failures DO throw - a human pressed a button and is waiting
 * for the answer.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CloudApiClient {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\d+)\\s*}}");

    /** Everything the mirror needs off a template node, list or single. */
    private static final String TEMPLATE_FIELDS =
            "id,name,language,category,status,components,rejected_reason";

    private final ApplicationProperties properties;
    private final RestClient rest;
    private final ObjectMapper mapper;

    /** The outcome of a best-effort send. {@code messageId} is Meta's {@code wamid}. */
    public record SendResult(boolean sent, String messageId, String failureReason) {

        public static SendResult failed(String reason) {
            return new SendResult(false, null, reason);
        }
    }

    /**
     * A media file already uploaded to Meta, to be used as a template's header.
     *
     * @param mediaId  what {@link CloudApiClient#uploadMedia} returned
     * @param fileName the name the recipient sees on the document
     * @param kind     {@code document} | {@code image} | {@code video}
     */
    public record HeaderMedia(String mediaId, String fileName, String kind) {}

    /**
     * One template send: which approved template, in which language, with the
     * values for its <code>{{1}}...{{n}}</code> placeholders, an optional header,
     * and an optional value for a dynamic URL button.
     *
     * <p>A header is either media or text, never both - Meta gives a template one
     * header of one format. {@code headerText} fills a TEXT header that carries a
     * placeholder; a static TEXT header takes nothing, and sending a value for
     * one is rejected exactly as omitting one for a dynamic header is.
     *
     * <p>{@code urlButtonParam} is what a template whose button URL ends in a
     * placeholder needs - Meta appends the value to the button's base URL. It is
     * how one template serves every teacher: the button reads
     * {@code https://wa.me/} and the teacher's own number is supplied per send.
     * A template with a STATIC button takes no value here, and sending one anyway
     * is rejected.
     */
    public record TemplateSpec(String name, String language, List<String> bodyParams,
            HeaderMedia header, String headerText, String urlButtonParam) {

        public static TemplateSpec of(String name, String language, List<String> bodyParams) {
            return new TemplateSpec(name, language, bodyParams, null, null, null);
        }
    }

    /** One number under the business account, as Meta currently reports it. */
    public record NumberInfo(String id, String displayPhoneNumber, String verifiedName,
            String qualityRating, String status, String codeVerificationStatus) {}

    /**
     * One message template, as Meta currently reports it.
     *
     * @param headerText   the wording of a TEXT header, null for any other format.
     *                     Whether it carries a placeholder is what decides if a
     *                     send must supply a header value
     * @param hasUrlButton whether a button's URL ends in a placeholder, which is
     *                     the only kind that takes a per-send value
     */
    public record TemplateInfo(String id, String name, String language, String category,
            String status, String bodyText, String headerFormat, String headerText, int bodyParams,
            boolean hasUrlButton, String rejectedReason) {}

    // ---- sending ------------------------------------------------------------

    /**
     * Free-form text. Meta accepts this ONLY inside the 24-hour window opened by
     * the recipient's own last message; outside it the call is rejected and the
     * message is never delivered. The rejection is returned as the failure reason
     * rather than hidden, because the fix is to use a template and the person
     * reading the log needs to be told that.
     */
    public SendResult sendText(String phoneNumberId, String phone, String body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", WaPhone.international(phone));
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", false, "body", body));
        return post(phoneNumberId, payload);
    }

    /** An approved template, with its placeholder values and optional media header. */
    public SendResult sendTemplate(String phoneNumberId, String phone, TemplateSpec spec) {
        List<Map<String, Object>> components = new ArrayList<>();

        if (spec.header() != null) {
            HeaderMedia media = spec.header();
            Map<String, Object> mediaObject = new LinkedHashMap<>();
            mediaObject.put("id", media.mediaId());
            if ("document".equals(media.kind()) && media.fileName() != null) {
                mediaObject.put("filename", media.fileName());
            }
            components.add(Map.of(
                    "type", "header",
                    "parameters", List.of(Map.of("type", media.kind(), media.kind(), mediaObject))));
        } else if (spec.headerText() != null && !spec.headerText().isBlank()) {
            // A TEXT header with a placeholder is a separate component from the
            // body and is counted separately: omitting it fails the send with
            // "number of localizable_params (0) does not match", no matter how
            // correct the body parameters are.
            components.add(Map.of(
                    "type", "header",
                    "parameters", List.of(Map.of("type", "text", "text", spec.headerText()))));
        }

        if (spec.bodyParams() != null && !spec.bodyParams().isEmpty()) {
            List<Map<String, Object>> params = spec.bodyParams().stream()
                    // Meta rejects a null parameter outright and silently mangles a
                    // blank one; an empty placeholder is a bug upstream, so make it
                    // visible rather than send half a sentence.
                    .map(value -> Map.<String, Object>of("type", "text",
                            "text", value == null || value.isBlank() ? "-" : value))
                    .toList();
            components.add(Map.of("type", "body", "parameters", params));
        }

        if (spec.urlButtonParam() != null && !spec.urlButtonParam().isBlank()) {
            // index is the button's position among the template's buttons. These
            // templates carry exactly one, so it is always 0; a template with more
            // would need the index carried alongside the value.
            components.add(Map.of(
                    "type", "button",
                    "sub_type", "url",
                    "index", "0",
                    "parameters", List.of(Map.of("type", "text", "text", spec.urlButtonParam()))));
        }

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", spec.name());
        template.put("language", Map.of("code", spec.language()));
        if (!components.isEmpty()) {
            template.put("components", components);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", WaPhone.international(phone));
        payload.put("type", "template");
        payload.put("template", template);
        return post(phoneNumberId, payload);
    }

    @SuppressWarnings("unchecked")
    private SendResult post(String phoneNumberId, Map<String, Object> payload) {
        if (!properties.meta().configured()) {
            return SendResult.failed("لم يتم إعداد واتساب");
        }
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            return SendResult.failed("الرقم غير مسجّل على واتساب");
        }
        try {
            Map<String, Object> res = rest.post()
                    .uri(graph("/" + phoneNumberId + "/messages"))
                    .headers(this::auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            String wamid = null;
            if (res != null && res.get("messages") instanceof List<?> messages && !messages.isEmpty()
                    && messages.get(0) instanceof Map<?, ?> first) {
                wamid = String.valueOf(first.get("id"));
            }
            log.info("Cloud API message accepted, wamid={}", wamid);
            return new SendResult(true, wamid, null);
        } catch (RestClientResponseException ex) {
            String reason = describe(ex);
            log.error("Cloud API send failed: {}", reason);
            return SendResult.failed(reason);
        } catch (RestClientException ex) {
            log.error("Cloud API send failed: {}", ex.getMessage());
            return SendResult.failed(ex.getMessage());
        }
    }

    // ---- media --------------------------------------------------------------

    /**
     * Uploads a file and returns Meta's media id, which a template header then
     * refers to. Meta keeps the media for 30 days; nothing here caches the id,
     * because the PDFs this sends (a barcode card, a report) are regenerated per
     * student and are never the same file twice.
     *
     * @return the media id, or null when the upload failed - the caller decides
     *         whether to fall back to a text-only send or record a failure
     */
    @SuppressWarnings("unchecked")
    public String uploadMedia(String phoneNumberId, byte[] content, String fileName, String mimeType) {
        requireConfigured();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("messaging_product", "whatsapp");
        form.add("type", mimeType);
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        try {
            Map<String, Object> res = rest.post()
                    .uri(graph("/" + phoneNumberId + "/media"))
                    .headers(this::auth)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return res == null ? null : String.valueOf(res.get("id"));
        } catch (RestClientResponseException ex) {
            log.error("Cloud API media upload failed: {}", describe(ex));
            return null;
        } catch (RestClientException ex) {
            log.error("Cloud API media upload failed: {}", ex.getMessage());
            return null;
        }
    }

    // ---- provisioning a number ---------------------------------------------

    /**
     * Adds a number to the business account and returns its {@code phone_number_id}.
     * The number is not usable yet: it must be verified by code and then registered.
     */
    @SuppressWarnings("unchecked")
    public String addNumber(String countryCode, String localNumber, String verifiedName) {
        requireConfigured();
        Map<String, Object> res = call(() -> rest.post()
                .uri(graph("/" + properties.meta().wabaId() + "/phone_numbers"))
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("cc", countryCode, "phone_number", localNumber,
                        "verified_name", verifiedName))
                .retrieve()
                .body(Map.class));
        return res == null ? null : String.valueOf(res.get("id"));
    }

    /** Asks Meta to send the verification code to the number, by SMS or a call. */
    @SuppressWarnings("unchecked")
    public void requestCode(String phoneNumberId, String method) {
        requireConfigured();
        call(() -> rest.post()
                .uri(graph("/" + phoneNumberId + "/request_code"))
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code_method", method, "language", "ar"))
                .retrieve()
                .body(Map.class));
    }

    /** Confirms the code the owner of the number read out. */
    @SuppressWarnings("unchecked")
    public void verifyCode(String phoneNumberId, String code) {
        requireConfigured();
        call(() -> rest.post()
                .uri(graph("/" + phoneNumberId + "/verify_code"))
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", code))
                .retrieve()
                .body(Map.class));
    }

    /**
     * Registers the verified number for Cloud API - the step that actually makes
     * it send. The PIN is the number's two-step verification: Meta requires one
     * and asks for it again on any future re-registration, so whoever sets it must
     * keep it.
     */
    @SuppressWarnings("unchecked")
    public void register(String phoneNumberId, String pin) {
        requireConfigured();
        call(() -> rest.post()
                .uri(graph("/" + phoneNumberId + "/register"))
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("messaging_product", "whatsapp", "pin", pin))
                .retrieve()
                .body(Map.class));
    }

    /** Removes the number from the business account entirely. */
    @SuppressWarnings("unchecked")
    public void deleteNumber(String phoneNumberId) {
        requireConfigured();
        call(() -> rest.delete()
                .uri(graph("/" + phoneNumberId))
                .headers(this::auth)
                .retrieve()
                .body(Map.class));
    }

    /**
     * Subscribes this app to the business account's webhooks. Without it Meta
     * accepts the sends but never reports what happened to them.
     */
    @SuppressWarnings("unchecked")
    public void subscribeApp() {
        requireConfigured();
        call(() -> rest.post()
                .uri(graph("/" + properties.meta().wabaId() + "/subscribed_apps"))
                .headers(this::auth)
                .retrieve()
                .body(Map.class));
    }

    /** Every number on the business account, with the state Meta reports right now. */
    public List<NumberInfo> listNumbers() {
        requireConfigured();
        JsonNode root = json(graph("/" + properties.meta().wabaId() + "/phone_numbers"
                + "?fields=id,display_phone_number,verified_name,quality_rating,status,"
                + "code_verification_status&limit=50"));
        List<NumberInfo> out = new ArrayList<>();
        for (JsonNode n : root.path("data")) {
            out.add(new NumberInfo(
                    text(n, "id"),
                    text(n, "display_phone_number"),
                    text(n, "verified_name"),
                    text(n, "quality_rating"),
                    text(n, "status"),
                    text(n, "code_verification_status")));
        }
        return out;
    }

    // ---- templates ----------------------------------------------------------

    /**
     * Every template on the business account. Meta owns them: they are authored
     * and reviewed in WhatsApp Manager, and this only mirrors what exists so the
     * app can show which ones it is allowed to send.
     */
    public List<TemplateInfo> listTemplates() {
        requireConfigured();
        JsonNode root = json(graph("/" + properties.meta().wabaId() + "/message_templates"
                + "?fields=" + TEMPLATE_FIELDS + "&limit=100"));
        List<TemplateInfo> out = new ArrayList<>();
        for (JsonNode n : root.path("data")) {
            out.add(readTemplate(n));
        }
        return out;
    }

    /**
     * One template, fetched by the id printed in WhatsApp Manager.
     *
     * <p>This is how a template is adopted without waiting for a full sync: a
     * person copies the id off the template's own page and the system reads the
     * real thing back - its body, its header, how many values it takes. Typing a
     * name instead would let a typo become a template that fails only when a
     * parent was due a message.
     */
    public TemplateInfo fetchTemplate(String metaTemplateId) {
        requireConfigured();
        JsonNode node = json(graph("/" + metaTemplateId + "?fields=" + TEMPLATE_FIELDS));
        if (text(node, "id") == null) {
            throw new BusinessRuleException("لا يوجد قالب بهذا المعرّف على حساب واتساب");
        }
        return readTemplate(node);
    }

    /** Meta's template node, flattened into what the mirror stores. */
    private static TemplateInfo readTemplate(JsonNode n) {
        String bodyText = null;
        String headerFormat = "NONE";
        String headerText = null;
        boolean urlButton = false;
        for (JsonNode component : n.path("components")) {
            String type = component.path("type").asText("");
            if ("BODY".equalsIgnoreCase(type)) {
                bodyText = text(component, "text");
            } else if ("HEADER".equalsIgnoreCase(type)) {
                String format = text(component, "format");
                headerFormat = format == null ? "TEXT" : format;
                if ("TEXT".equalsIgnoreCase(headerFormat)) {
                    headerText = text(component, "text");
                }
            } else if ("BUTTONS".equalsIgnoreCase(type)) {
                for (JsonNode button : component.path("buttons")) {
                    // A static URL button carries no placeholder, and sending a
                    // value for one is rejected outright - so only a URL that ends
                    // in {{1}} counts as "takes a value".
                    String url = text(button, "url");
                    if ("URL".equalsIgnoreCase(button.path("type").asText(""))
                            && url != null && PLACEHOLDER.matcher(url).find()) {
                        urlButton = true;
                    }
                }
            }
        }
        return new TemplateInfo(
                text(n, "id"),
                text(n, "name"),
                text(n, "language"),
                text(n, "category"),
                text(n, "status"),
                bodyText,
                headerFormat,
                headerText,
                countPlaceholders(bodyText),
                urlButton,
                text(n, "rejected_reason"));
    }

    /**
     * How many parameters a body takes. The HIGHEST index is the answer, not the
     * number of occurrences: a template may repeat <code>{{1}}</code> and is still
     * sent one parameter, and Meta rejects a send whose parameter count does not
     * match.
     */
    static int countPlaceholders(String bodyText) {
        if (bodyText == null) {
            return 0;
        }
        Matcher matcher = PLACEHOLDER.matcher(bodyText);
        int highest = 0;
        while (matcher.find()) {
            highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
        }
        return highest;
    }

    // ---- plumbing -----------------------------------------------------------

    private String graph(String path) {
        return properties.meta().graphBase() + path;
    }

    private void auth(HttpHeaders headers) {
        headers.setBearerAuth(properties.meta().accessToken());
    }

    private void requireConfigured() {
        if (!properties.meta().configured()) {
            throw new BusinessRuleException(
                    "واتساب غير مُعد على الخادم (META_ACCESS_TOKEN / META_WABA_ID)");
        }
    }

    /** {@code null} rather than the literal string "null" for an absent field. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private JsonNode json(String uri) {
        String body;
        try {
            body = rest.get().uri(uri).headers(this::auth).retrieve().body(String.class);
        } catch (RestClientResponseException ex) {
            throw new BusinessRuleException(describe(ex));
        } catch (RestClientException ex) {
            throw new BusinessRuleException("تعذّر الاتصال بواتساب: " + ex.getMessage());
        }
        try {
            return mapper.readTree(body == null ? "{}" : body);
        } catch (Exception ex) {
            throw new BusinessRuleException("رد غير مفهوم من واتساب");
        }
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException ex) {
            throw new BusinessRuleException(describe(ex));
        } catch (RestClientException ex) {
            throw new BusinessRuleException("تعذّر الاتصال بواتساب: " + ex.getMessage());
        }
    }

    /**
     * Meta's errors carry the useful part in {@code error.error_data.details} and
     * a vaguer sentence in {@code error.message}; the HTTP status says almost
     * nothing. Prefer the details, fall back to the message, and only then to the
     * status - whatever comes out of here is what a teacher reads in the log.
     */
    private String describe(RestClientResponseException ex) {
        try {
            JsonNode error = mapper.readTree(ex.getResponseBodyAsString()).path("error");
            String details = text(error.path("error_data"), "details");
            if (details != null && !details.isBlank()) {
                return details;
            }
            String message = text(error, "message");
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // Fall through to the status line below.
        }
        return "واتساب رفض الطلب (" + ex.getStatusCode().value() + ")";
    }
}
