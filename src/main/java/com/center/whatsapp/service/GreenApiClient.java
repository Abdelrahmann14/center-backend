package com.center.whatsapp.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.center.common.exception.BusinessRuleException;
import com.center.whatsapp.service.WhatsappInstanceService.Creds;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends WhatsApp messages through Green API. Used to deliver student
 * registration verification codes.
 *
 * <p>Credentials are resolved via {@link WhatsappInstanceService}: the instance
 * linked in-app (Services page) wins, falling back to {@code .env} config. When no
 * instance is configured the send is skipped and logged, so the flow still works
 * end-to-end in development - the code is read from the server log instead.
 */
@Service
@Slf4j
public class GreenApiClient {

    private static final String SEND_PATH = "/waInstance{instanceId}/sendMessage/{apiToken}";
    private static final String CHECK_PATH = "/waInstance{instanceId}/checkWhatsapp/{apiToken}";
    private static final String SEND_FILE_PATH = "/waInstance{instanceId}/sendFileByUpload/{apiToken}";

    private final WhatsappInstanceService instances;
    private final ApplicationEventPublisher events;
    private final RestClient rest;

    public GreenApiClient(WhatsappInstanceService instances, ApplicationEventPublisher events,
            RestClient rest) {
        this.instances = instances;
        this.events = events;
        this.rest = rest;
    }

    /** Send a plain-text WhatsApp message using any connected number. */
    public void sendText(String phone, String message) {
        send(instances.resolve(), "broadcast", phone, message);
    }

    /**
     * Send a message for a specific purpose, routed to the number responsible for
     * it (with automatic failover to a backup, then {@code .env}).
     */
    public void sendText(String responsibilityCode, String phone, String message) {
        send(instances.resolveFor(responsibilityCode), responsibilityCode, phone, message);
    }

    /** The outcome of a best-effort send: {@code sent=false} carries the reason. */
    public record SendOutcome(boolean sent, String failureReason) {}

    /**
     * Send routed to a responsibility, reporting the outcome instead of throwing.
     * Used by the messaging log, where every attempt - success or failure - is
     * recorded per recipient rather than aborting a batch on the first error.
     */
    public SendOutcome trySend(String responsibilityCode, String phone, String message) {
        Creds creds = instances.resolveFor(responsibilityCode);
        if (!creds.configured()) {
            return new SendOutcome(false, "لم يتم ربط رقم واتساب");
        }
        String chatId = toChatId(phone);
        try {
            rest.post()
                    .uri(creds.baseUrl() + SEND_PATH, creds.instanceId(), creds.apiToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chatId", chatId, "message", message))
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp message sent to {}", chatId);
            return new SendOutcome(true, null);
        } catch (RestClientException ex) {
            log.error("Green API send failed for {}: {}", chatId, ex.getMessage());
            return new SendOutcome(false, ex.getMessage());
        }
    }

    /**
     * Upload a file (image/document) routed to a responsibility, reporting the
     * outcome instead of throwing. Used by the messaging log when a template is set
     * to send as an image: every attempt is recorded per recipient, like {@link
     * #trySend}.
     */
    public SendOutcome trySendFile(String responsibilityCode, String phone, byte[] content,
            String fileName, String caption) {
        Creds creds = instances.resolveFor(responsibilityCode);
        if (!creds.configured()) {
            return new SendOutcome(false, "لم يتم ربط رقم واتساب");
        }
        String chatId = toChatId(phone);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("chatId", chatId);
        form.add("fileName", fileName);
        if (caption != null && !caption.isBlank()) {
            form.add("caption", caption);
        }
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        try {
            rest.post()
                    .uri(creds.baseUrl() + SEND_FILE_PATH, creds.instanceId(), creds.apiToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp image '{}' sent to {}", fileName, chatId);
            return new SendOutcome(true, null);
        } catch (RestClientException ex) {
            log.error("Green API sendFileByUpload failed for {}: {}", chatId, ex.getMessage());
            return new SendOutcome(false, ex.getMessage());
        }
    }

    /**
     * Upload a document to a chat (Green API {@code sendFileByUpload}), used to
     * deliver student report PDFs. Unlike a text send, a failure here is surfaced
     * to the caller: the teacher pressed "send" and must know it did not arrive.
     */
    public void sendDocument(String phone, byte[] content, String fileName, String caption, String purpose) {
        sendDocument(phone, content, fileName, caption, purpose, null);
    }

    /**
     * As above, naming the student the document is about. The message history
     * records the recipient either way - it can resolve a number to its owner -
     * but a caller that already knows the subject should say so: a report sent to
     * a guardian's number is still that student's report.
     */
    public void sendDocument(String phone, byte[] content, String fileName, String caption,
            String purpose, java.util.UUID studentId) {
        String logBody = caption != null && !caption.isBlank() ? caption : fileName;
        Creds creds = instances.resolve();
        if (!creds.configured()) {
            events.publishEvent(new WhatsappSendEvent(
                    phone, logBody, purpose, false, "لم يتم ربط رقم واتساب", studentId));
            throw new BusinessRuleException("لم يتم ربط رقم واتساب بعد، افتح صفحة تكامل الخدمات لربطه");
        }
        String chatId = toChatId(phone);
        // WhatsApp and Green API can serve a previously delivered document when a
        // new send reuses the exact same file name (the recipient's phone opens the
        // cached copy already in its Documents folder). A short hash of the content,
        // folded into the name, keeps it stable while the PDF is unchanged but
        // changes it the moment the PDF does - so a redesigned card is never masked
        // by a stale copy of the old one.
        String uploadName = cacheBustedName(fileName, content);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("chatId", chatId);
        form.add("fileName", uploadName);
        if (caption != null && !caption.isBlank()) {
            form.add("caption", caption);
        }
        // Green API reads the upload from the "file" part; the resource must
        // report a filename or the part is rejected as a plain field.
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return uploadName;
            }
        });
        try {
            rest.post()
                    .uri(creds.baseUrl() + SEND_FILE_PATH, creds.instanceId(), creds.apiToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp document '{}' sent to {}", uploadName, chatId);
            events.publishEvent(new WhatsappSendEvent(phone, logBody, purpose, true, null, studentId));
        } catch (RestClientException ex) {
            log.error("Green API sendFileByUpload failed for {}: {}", chatId, ex.getMessage());
            events.publishEvent(new WhatsappSendEvent(
                    phone, logBody, purpose, false, ex.getMessage(), studentId));
            throw new BusinessRuleException("تعذّر إرسال الملف عبر واتساب، حاول مرة أخرى");
        }
    }

    /** Fold a short content hash into the file name, before the extension. */
    private static String cacheBustedName(String fileName, byte[] content) {
        String tag = contentTag(content);
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + " (" + tag + ")";
        }
        return fileName.substring(0, dot) + " (" + tag + ")" + fileName.substring(dot);
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** First three bytes of the content's SHA-256, as six hex chars. */
    private static String contentTag(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            char[] out = new char[6];
            for (int i = 0; i < 3; i++) {
                out[i * 2] = HEX[(digest[i] >> 4) & 0xF];
                out[i * 2 + 1] = HEX[digest[i] & 0xF];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(content.length);
        }
    }

    private void send(Creds creds, String purpose, String phone, String message) {
        if (!creds.configured()) {
            log.warn("Green API not configured - WhatsApp message to {} NOT sent.", phone);
            events.publishEvent(new WhatsappSendEvent(phone, message, purpose, false, "لم يتم ربط رقم واتساب"));
            return;
        }
        String chatId = toChatId(phone);
        try {
            rest.post()
                    .uri(creds.baseUrl() + SEND_PATH, creds.instanceId(), creds.apiToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chatId", chatId, "message", message))
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp message sent to {}", chatId);
            events.publishEvent(new WhatsappSendEvent(phone, message, purpose, true, null));
        } catch (RestClientException ex) {
            log.error("Green API send failed for {}: {}", chatId, ex.getMessage());
            events.publishEvent(new WhatsappSendEvent(phone, message, purpose, false, ex.getMessage()));
            throw new BusinessRuleException("تعذّر إرسال رمز التحقق عبر واتساب، حاول مرة أخرى");
        }
    }

    /** Outcome of a WhatsApp check. {@code checked=false} means it could not be
     *  verified (Green API not configured, or the call failed) - callers treat an
     *  unverified number as acceptable so a service outage never blocks a flow. */
    public record WhatsappCheck(boolean existsWhatsapp, boolean checked) {}

    /** Whether a phone number is registered on WhatsApp (via Green API). */
    @SuppressWarnings("unchecked")
    public WhatsappCheck checkWhatsapp(String phone) {
        Creds creds = instances.resolve();
        if (!creds.configured()) {
            return new WhatsappCheck(true, false);
        }
        String chatId = toChatId(phone);
        try {
            Map<String, Object> res = rest.post()
                    .uri(creds.baseUrl() + CHECK_PATH, creds.instanceId(), creds.apiToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chatId", chatId))
                    .retrieve()
                    .body(Map.class);
            boolean exists = res != null && Boolean.TRUE.equals(res.get("existsWhatsapp"));
            return new WhatsappCheck(exists, true);
        } catch (RestClientException ex) {
            log.error("Green API checkWhatsapp failed for {}: {}", chatId, ex.getMessage());
            return new WhatsappCheck(true, false);
        }
    }

    /**
     * Green API chat id for an Egyptian number: local "01xxxxxxxxx" becomes the
     * international "201xxxxxxxxx@c.us".
     */
    static String toChatId(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.startsWith("0")) {
            digits = "20" + digits.substring(1);
        } else if (!digits.startsWith("20")) {
            digits = "20" + digits;
        }
        return digits + "@c.us";
    }
}
