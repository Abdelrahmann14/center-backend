package com.center.whatsapp.cloud.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.config.ApplicationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies and applies the events Meta pushes about the official WhatsApp
 * account.
 *
 * <p>Writes go through {@link JdbcTemplate} rather than the repositories on
 * purpose: {@code wa_message_log} is tenant-scoped by Hibernate, and a webhook
 * arrives with no logged-in user and therefore no tenant - a JPA update would
 * quietly match nothing. The message id Meta sends is globally unique, so
 * addressing the row by it is both correct and sufficient.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappWebhookService {

    private static final String HMAC = "HmacSHA256";

    private final ApplicationProperties properties;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final CloudTemplateService templates;

    /**
     * Whether {@code signature} is Meta's HMAC of exactly this body, using the app
     * secret. Compared in constant time - a byte-by-byte comparison that returns
     * early leaks, over many attempts, how much of a guess was right.
     *
     * <p>No app secret configured means no way to tell a real event from a forged
     * one, so everything is refused rather than trusted.
     */
    public boolean signatureMatches(String signature, String body) {
        String secret = properties.meta().appSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("META_APP_SECRET is not set - webhook payloads cannot be verified");
            return false;
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(hex(computed).getBytes(StandardCharsets.UTF_8),
                    signature.substring("sha256=".length()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Could not verify a webhook signature: {}", ex.getMessage());
            return false;
        }
    }

    /** Applies every event in one payload. Unknown fields are ignored, not errors. */
    @Transactional
    public void handle(String body) throws RuntimeException {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed webhook payload", ex);
        }

        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                String field = change.path("field").asText("");
                JsonNode value = change.path("value");
                switch (field) {
                    case "messages" -> messages(value);
                    case "message_template_status_update" -> templateStatus(value);
                    default -> log.debug("Ignoring webhook field {}", field);
                }
            }
        }
    }

    /**
     * Delivery reports for messages we sent, and anything a customer sent us.
     *
     * <p>A status only ever moves forward - sent, delivered, read - and Meta may
     * repeat or reorder them, so each update guards on the column it fills being
     * still empty. Without that a late "delivered" retry could stamp a time after
     * the "read" that already landed.
     */
    private void messages(JsonNode value) {
        for (JsonNode status : value.path("statuses")) {
            String wamid = status.path("id").asText(null);
            String state = status.path("status").asText("");
            if (wamid == null) {
                continue;
            }
            OffsetDateTime at = epochSeconds(status.path("timestamp").asText(null));
            switch (state) {
                case "delivered" -> jdbc.update(
                        "update wa_message_log set delivered_at = ? "
                                + "where wamid = ? and delivered_at is null",
                        at, wamid);
                case "read" -> jdbc.update(
                        "update wa_message_log set read_at = ?, "
                                + "delivered_at = coalesce(delivered_at, ?) "
                                + "where wamid = ? and read_at is null",
                        at, at, wamid);
                case "failed" -> jdbc.update(
                        "update wa_message_log set status = 'FAILED', failure_reason = ?, "
                                + "failure_code = ? where wamid = ?",
                        failureReason(status), failureCode(status), wamid);
                default -> log.debug("Ignoring message status {}", state);
            }
        }

        // An inbound message opens the 24-hour window in which free-form replies
        // are allowed. Nothing acts on that yet; logging it keeps the arrival
        // visible while the reply side is built.
        for (JsonNode message : value.path("messages")) {
            log.info("Inbound WhatsApp message from {}", message.path("from").asText("?"));
        }
    }

    /** Meta finished reviewing a template - approved, rejected, or paused. */
    private void templateStatus(JsonNode value) {
        String templateId = value.path("message_template_id").asText(null);
        String event = value.path("event").asText(null);
        if (templateId == null || event == null) {
            return;
        }
        String reason = value.path("reason").asText(null);
        templates.applyStatusUpdate(templateId, event.toUpperCase(), reason);
        log.info("Template {} is now {}", templateId, event);
    }

    /**
     * The readable half of a failure. Meta nests the useful sentence under
     * {@code errors[0].error_data.details} and a shorter one in {@code title}.
     */
    private static String failureReason(JsonNode status) {
        JsonNode error = status.path("errors").path(0);
        String details = error.path("error_data").path("details").asText(null);
        if (details != null && !details.isBlank()) {
            return details;
        }
        String title = error.path("title").asText(null);
        return title == null || title.isBlank() ? "رفضت واتساب الرسالة" : title;
    }

    /**
     * Meta's numeric code for the failure, or null when it did not send one.
     *
     * <p>This is the half of a failure that can be acted on. 131026 in
     * particular means the message could not be put in front of the recipient
     * at all - most often because the number is not on WhatsApp - which is what
     * the platform reads to answer "can we reach this family".
     */
    private static Integer failureCode(JsonNode status) {
        JsonNode code = status.path("errors").path(0).path("code");
        return code.isInt() ? code.asInt() : null;
    }

    private static OffsetDateTime epochSeconds(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value)).atOffset(ZoneOffset.UTC);
        } catch (NumberFormatException ex) {
            return OffsetDateTime.now();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
