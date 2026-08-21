package com.center.messaging.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.center.messaging.entity.WhatsappMessageLog;
import com.center.messaging.repository.WhatsappMessageLogRepository;
import com.center.whatsapp.cloud.service.CloudApiClient;
import com.center.whatsapp.cloud.service.CloudMessageResolver;
import com.center.whatsapp.service.WhatsappInstanceService;
import com.center.whatsapp.service.WhatsappInstanceService.Creds;
import com.center.whatsapp.service.WhatsappResponsibilityCatalog;

import lombok.RequiredArgsConstructor;

/**
 * Sends one WhatsApp message and records the attempt in {@code wa_message_log}.
 * Every send in the messaging feature goes through here, so the history table sees
 * every message - delivered or failed - with its reason. Must run inside a
 * tenant-bound transaction so the log row is stamped with the workspace.
 *
 * <p>Two things are decided here and nowhere else.
 *
 * <p><b>Which number.</b> The message's {@code origin} names a message type, and
 * the type has a number assigned to it. Before this, every message left through
 * the {@code broadcast} number regardless, which made the per-type assignment a
 * label rather than a setting.
 *
 * <p><b>Which template.</b> Free text reaches a person only inside the 24 hours
 * after their own last message, so a message the system starts must go out as an
 * approved template. The template is resolved from the message type and filled
 * from the same variables the text was rendered from; a type with no template is
 * recorded as failed, saying exactly that, rather than being fired at WhatsApp to
 * be rejected.
 */
@Component
@RequiredArgsConstructor
public class WhatsappLogSender {

    private final CloudApiClient cloudApiClient;
    private final CloudMessageResolver cloudTemplates;
    private final WhatsappInstanceService instances;
    private final WhatsappMessageLogRepository logRepository;

    /** One resolved recipient of a message. */
    public record Recipient(String name, String phone, String code, String type, UUID studentId) {}

    /** What one attempt produced, before it is written to the log row. */
    private record Attempt(boolean sent, String failureReason, String messageId) {}

    /**
     * Send {@code body} to one recipient and log the result. {@code lectureId}/
     * {@code groupId} tie the row to a lesson (null for a broadcast). Returns true
     * when the message was accepted by WhatsApp.
     */
    public boolean logAndSend(Recipient recipient, String body, String source, String origin,
            UUID lectureId, UUID groupId, UUID sentByUserId, String sentByName) {
        return logAndSend(recipient, body, source, origin, lectureId, groupId, sentByUserId,
                sentByName, null);
    }

    /**
     * As above, carrying the variables the body was rendered from.
     *
     * <p>They are what fills the template's numbered placeholders, so a caller
     * that has them should pass them: without them only a template whose body
     * takes no values at all can be sent.
     */
    public boolean logAndSend(Recipient recipient, String body, String source, String origin,
            UUID lectureId, UUID groupId, UUID sentByUserId, String sentByName,
            Map<String, String> vars) {
        WhatsappMessageLog row = new WhatsappMessageLog();
        row.setRecipientName(recipient.name());
        row.setPhone(recipient.phone());
        row.setRecipientCode(recipient.code());
        row.setRecipientType(recipient.type());
        row.setStudentId(recipient.studentId());
        row.setLectureId(lectureId);
        row.setGroupId(groupId);
        row.setBody(body);
        row.setSource(source);
        row.setOrigin(origin);
        row.setSentByUserId(sentByUserId);
        row.setSentByName(sentByName);

        Attempt attempt = deliver(recipient, null, null, vars, row);
        return finish(row, attempt);
    }

    /**
     * Send {@code caption} to one recipient with {@code document} attached (a PDF
     * card, say), and log the result. Used by the new-student flow, which ships the
     * barcode card with the welcome text.
     */
    public boolean logAndSendFile(Recipient recipient, String caption, byte[] document,
            String fileName, String source, String origin) {
        return logAndSendFile(recipient, caption, document, fileName, source, origin, null);
    }

    /** As above, carrying the variables the caption was rendered from. */
    public boolean logAndSendFile(Recipient recipient, String caption, byte[] document,
            String fileName, String source, String origin, Map<String, String> vars) {
        return sendFile(recipient, caption, document, fileName, source, origin, vars).sent();
    }

    /** What one send did, for a caller that has to explain a refusal. */
    public record Outcome(boolean sent, String failureReason) {
    }

    /**
     * As {@link #logAndSendFile}, also answering WHY it did not go.
     *
     * <p>The reason is already written to the log row; returning it as well is
     * what lets the button that triggered the send say the same thing the history
     * says, instead of the caller inventing a second, vaguer wording of its own.
     */
    public Outcome sendFile(Recipient recipient, String caption, byte[] document,
            String fileName, String source, String origin, Map<String, String> vars) {
        WhatsappMessageLog row = new WhatsappMessageLog();
        row.setRecipientName(recipient.name());
        row.setPhone(recipient.phone());
        row.setRecipientCode(recipient.code());
        row.setRecipientType(recipient.type());
        row.setStudentId(recipient.studentId());
        row.setBody(caption);
        row.setSource(source);
        row.setOrigin(origin);

        String name = fileName == null || fileName.isBlank() ? "file" : fileName;
        Attempt attempt = deliver(recipient, document, name, vars, row);
        finish(row, attempt);
        return new Outcome(attempt.sent(), attempt.failureReason());
    }

    private boolean finish(WhatsappMessageLog row, Attempt attempt) {
        row.setStatus(attempt.sent() ? "SENT" : "FAILED");
        row.setFailureReason(attempt.failureReason());
        row.setWamid(attempt.messageId());
        logRepository.save(row);
        return attempt.sent();
    }

    /**
     * One attempt at one recipient, through the number assigned to this message
     * type. {@code document} non-null means "attach this file", which becomes the
     * template's header - the upload happens here and not at the call site,
     * because the media id is only meaningful to the number that uploaded it.
     */
    private Attempt deliver(Recipient recipient, byte[] document, String fileName,
            Map<String, String> vars, WhatsappMessageLog row) {
        if (recipient.phone() == null || recipient.phone().isBlank()) {
            return new Attempt(false, "لا يوجد رقم هاتف للمستلم", null);
        }

        String code = WhatsappResponsibilityCatalog.forOrigin(row.getOrigin());
        Creds creds = instances.resolveFor(code);
        row.setInstanceId(creds.rowId());
        if (!creds.configured()) {
            return new Attempt(false, "لم يتم تفعيل رقم واتساب", null);
        }

        CloudMessageResolver.Resolved template =
                cloudTemplates.forCode(code, vars, creds.phone()).orElse(null);
        if (template == null) {
            return new Attempt(false, needsTemplate(code), null);
        }
        row.setTemplateName(template.name());
        row.setTemplateCategory(template.category());

        CloudApiClient.HeaderMedia header = null;
        if (document != null && document.length > 0) {
            if (!template.wantsDocument()) {
                return new Attempt(false,
                        "القالب المرتبط بهذا النوع لا يحتوي على رأس ملف، فلا يمكنه حمل المرفق", null);
            }
            String mediaId = cloudApiClient.uploadMedia(creds.phoneNumberId(), document, fileName,
                    "application/pdf");
            if (mediaId == null) {
                return new Attempt(false, "تعذّر رفع الملف إلى واتساب", null);
            }
            header = new CloudApiClient.HeaderMedia(mediaId, fileName, "document");
        }

        CloudApiClient.SendResult result = cloudApiClient.sendTemplate(creds.phoneNumberId(),
                recipient.phone(),
                new CloudApiClient.TemplateSpec(template.name(), template.language(),
                        template.params(), header, template.headerParam(),
                        template.urlButtonParam()));
        return new Attempt(result.sent(), result.failureReason(), result.messageId());
    }

    /**
     * Nothing is bound to this message type, so nothing can go out - WhatsApp
     * only carries a business-initiated message inside a template it reviewed.
     *
     * <p>The type is NAMED rather than called "this type". The reason is read in
     * two places that have lost the context by then - a history row long after
     * the fact, and a toast on a page that is not the Messages page - and
     * "اربطها بقالب" is only actionable if the reader knows which of the six to
     * go and bind. It is also not a send failure and must not read like one:
     * nothing was attempted, because there was nothing to attempt with.
     */
    static String needsTemplate(String code) {
        WhatsappResponsibilityCatalog.Responsibility r =
                WhatsappResponsibilityCatalog.find(code);
        String label = r == null ? code : r.label();
        return "لا توجد رسالة مثبتة لـ«" + label + "» — اربطها بقالب معتمد من الخدمات ← الرسائل";
    }
}
