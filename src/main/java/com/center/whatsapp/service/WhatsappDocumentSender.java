package com.center.whatsapp.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.center.common.exception.BusinessRuleException;
import com.center.whatsapp.cloud.service.CloudApiClient;
import com.center.whatsapp.cloud.service.CloudMessageResolver;
import com.center.whatsapp.service.WhatsappInstanceService.Creds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends a PDF - a barcode card, a student report, an invoice - to one number.
 *
 * <p>These sends never went through the messaging log: a teacher presses a button
 * on a student and the file goes out immediately. WhatsApp will not accept a file
 * a business sends unprompted unless it rides on an approved template whose header
 * is declared as a DOCUMENT: the file becomes that header, and the template's own
 * text becomes the caption.
 *
 * <p>So the three callers hand over a PDF and this decides how it reaches a phone.
 * A failure throws, because a person is standing in front of the screen waiting to
 * hear whether the report they just sent arrived.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappDocumentSender {

    private static final String PDF = "application/pdf";

    private final WhatsappInstanceService instances;
    private final CloudApiClient cloudApi;
    private final CloudMessageResolver cloudTemplates;
    private final ApplicationEventPublisher events;

    /**
     * @param purpose   the message kind, e.g. {@code BARCODE} / {@code REPORT} -
     *                  it picks both the responsible number and the template
     * @param studentId the student the document is about, when known
     * @param vars      the variables the template's placeholders are filled from;
     *                  null for a document with no template values
     */
    public void send(String phone, byte[] content, String fileName, String caption, String purpose,
            UUID studentId, Map<String, String> vars) {
        String code = WhatsappResponsibilityCatalog.forOrigin(purpose);
        Creds creds = instances.resolveFor(code);
        String logBody = caption != null && !caption.isBlank() ? caption : fileName;

        if (!creds.configured()) {
            publish(creds, phone, logBody, purpose, studentId, false, creds.reasonOrDefault(),
                    null, null);
            throw new BusinessRuleException(creds.reasonOrDefault());
        }
        sendOfficial(creds, code, phone, content, cacheBustedName(fileName, content), purpose,
                studentId, vars);
    }

    /** As above, for a document that carries no template variables. */
    public void send(String phone, byte[] content, String fileName, String caption, String purpose,
            UUID studentId) {
        send(phone, content, fileName, caption, purpose, studentId, null);
    }

    /**
     * Upload the file, then send the template that declares a DOCUMENT header
     * with the uploaded file as that header.
     *
     * <p>Every refusal below is a configuration problem with a specific fix, so
     * each one says which - "لا يوجد قالب" and "القالب بلا رأس ملف" send a person
     * to two different screens, and collapsing them into "failed to send" would
     * hide that.
     */
    private void sendOfficial(Creds creds, String code, String phone, byte[] content,
            String fileName, String purpose, UUID studentId, Map<String, String> vars) {
        String logBody = fileName;
        CloudMessageResolver.Resolved template =
                cloudTemplates.forCode(code, vars, creds.phone()).orElse(null);
        if (template == null) {
            publish(creds, phone, logBody, purpose, studentId, false,
                    "لا يوجد قالب معتمد لهذا النوع من الرسائل", null, null);
            throw new BusinessRuleException(
                    "إرسال الملف يحتاج قالباً معتمداً لهذا النوع. اربط القالب من إعدادات واتساب.");
        }
        if (!template.wantsDocument()) {
            publish(creds, phone, logBody, purpose, studentId, false,
                    "القالب المرتبط لا يحتوي على رأس ملف", template.name(), template.category());
            throw new BusinessRuleException(
                    "القالب المرتبط بهذه الرسالة لا يحتوي على رأس ملف، فلا يمكنه حمل المرفق.");
        }

        String mediaId = cloudApi.uploadMedia(creds.phoneNumberId(), content, fileName, PDF);
        if (mediaId == null) {
            publish(creds, phone, logBody, purpose, studentId, false,
                    "تعذّر رفع الملف إلى واتساب", template.name(), template.category());
            throw new BusinessRuleException("تعذّر رفع الملف إلى واتساب، حاول مرة أخرى");
        }

        CloudApiClient.SendResult result = cloudApi.sendTemplate(creds.phoneNumberId(), phone,
                new CloudApiClient.TemplateSpec(template.name(), template.language(),
                        template.params(),
                        new CloudApiClient.HeaderMedia(mediaId, fileName, "document"),
                        // A DOCUMENT header IS the header; there is no text slot
                        // to fill beside it.
                        null,
                        template.urlButtonParam()));
        publish(creds, phone, logBody, purpose, studentId, result.sent(), result.failureReason(),
                template.name(), template.category());
        if (!result.sent()) {
            throw new BusinessRuleException("تعذّر إرسال الملف عبر واتساب، حاول مرة أخرى");
        }
    }

    /**
     * Fold a short content hash into the file name, before the extension.
     *
     * <p>WhatsApp and the recipient's own phone will happily serve a previously
     * delivered document when a new send reuses the exact same file name - the
     * phone opens the copy already in its Documents folder. The hash keeps the
     * name stable while the PDF is unchanged and changes it the moment the PDF
     * does, so a redesigned card is never masked by a stale copy of the old one.
     */
    public static String cacheBustedName(String fileName, byte[] content) {
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

    private void publish(Creds creds, String phone, String body, String purpose, UUID studentId,
            boolean sent, String reason, String templateName, String templateCategory) {
        events.publishEvent(new WhatsappSendEvent(phone, body, purpose, sent, reason, studentId,
                creds.rowId(), templateName, templateCategory));
    }
}
