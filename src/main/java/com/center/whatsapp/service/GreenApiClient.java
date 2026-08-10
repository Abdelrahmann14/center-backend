package com.center.whatsapp.service;

import java.util.Map;

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
    private final RestClient rest = RestClient.create();

    public GreenApiClient(WhatsappInstanceService instances) {
        this.instances = instances;
    }

    /** Send a plain-text WhatsApp message using any connected number. */
    public void sendText(String phone, String message) {
        send(instances.resolve(), phone, message);
    }

    /**
     * Send a message for a specific purpose, routed to the number responsible for
     * it (with automatic failover to a backup, then {@code .env}).
     */
    public void sendText(String responsibilityCode, String phone, String message) {
        send(instances.resolveFor(responsibilityCode), phone, message);
    }

    /**
     * Upload a document to a chat (Green API {@code sendFileByUpload}), used to
     * deliver student report PDFs. Unlike a text send, a failure here is surfaced
     * to the caller: the teacher pressed "send" and must know it did not arrive.
     */
    public void sendDocument(String phone, byte[] content, String fileName, String caption) {
        Creds creds = instances.resolve();
        if (!creds.configured()) {
            throw new BusinessRuleException("لم يتم ربط رقم واتساب بعد، افتح صفحة تكامل الخدمات لربطه");
        }
        String chatId = toChatId(phone);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("chatId", chatId);
        form.add("fileName", fileName);
        if (caption != null && !caption.isBlank()) {
            form.add("caption", caption);
        }
        // Green API reads the upload from the "file" part; the resource must
        // report a filename or the part is rejected as a plain field.
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
            log.info("WhatsApp document '{}' sent to {}", fileName, chatId);
        } catch (RestClientException ex) {
            log.error("Green API sendFileByUpload failed for {}: {}", chatId, ex.getMessage());
            throw new BusinessRuleException("تعذّر إرسال الملف عبر واتساب، حاول مرة أخرى");
        }
    }

    private void send(Creds creds, String phone, String message) {
        if (!creds.configured()) {
            log.warn("Green API not configured - WhatsApp message to {} NOT sent.", phone);
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
        } catch (RestClientException ex) {
            log.error("Green API send failed for {}: {}", chatId, ex.getMessage());
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
