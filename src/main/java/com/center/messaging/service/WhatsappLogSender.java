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
    private final com.center.whatsapp.cloud.service.WhatsappThrottle throttle;

    /** One resolved recipient of a message. */
    public record Recipient(String name, String phone, String code, String type, UUID studentId) {}

    /**
     * What one attempt produced, before it is written to the log row.
     *
     * <p>{@code errorCode} is Meta's number, or null when the refusal came from
     * this side (no phone, no template, no number configured) and there was
     * never a call to Meta at all. The distinction matters to the queue: a local
     * refusal is permanent until a human changes something, while a Meta code
     * says whether to wait, skip the recipient, or stop the run.
     */
    private record Attempt(boolean sent, String failureReason, String messageId,
            Integer errorCode) {

        static Attempt refused(String reason) {
            return new Attempt(false, reason, null, null);
        }
    }

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
        return send(recipient, body, source, origin, lectureId, groupId, sentByUserId, sentByName,
                vars, null).sent();
    }

    /**
     * What one send did, in the detail a queue needs.
     *
     * <p>{@link #logAndSend} answers a boolean, which is all a synchronous
     * caller ever wanted: it was going to move to the next recipient either way.
     * A queue cannot work with that. It has to decide whether to retry this row
     * in thirty seconds, hold it for a day, drop it forever, or stop the entire
     * drain - and those four are distinguished only by Meta's numeric code.
     */
    public record Delivery(boolean sent, String failureReason, Integer errorCode, UUID logId) {

        /** Wait and try this same row again. */
        public boolean retryable() {
            return !sent && errorCode != null && (errorCode == 130429 || errorCode == 131057);
        }

        /** Hold this recipient, carry on with everyone else. */
        public boolean recipientBackoff() {
            return !sent && errorCode != null && (errorCode == 131056 || errorCode == 131049);
        }

        /** Stop the drain. Sending more makes this worse, not better. */
        public boolean fatal() {
            return !sent && errorCode != null && (errorCode == 131048 || errorCode == 368
                    || errorCode == 80007 || errorCode == 190 || errorCode == 133010);
        }
    }

    /**
     * Send one message and report exactly what happened, for a caller that has
     * to decide what to do about it.
     *
     * <p>{@code batchId} ties the log row back to the press that ordered it.
     */
    public Delivery send(Recipient recipient, String body, String source, String origin,
            UUID lectureId, UUID groupId, UUID sentByUserId, String sentByName,
            Map<String, String> vars, UUID batchId) {
        WhatsappMessageLog row = new WhatsappMessageLog();
        row.setBatchId(batchId);
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
        boolean sent = finish(row, attempt);
        return new Delivery(sent, attempt.failureReason(), attempt.errorCode(), row.getId());
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

    /**
     * Write the outcome to the log row.
     *
     * <p>{@code failure_code} has existed since V83 but only the webhook ever
     * filled it, and only for failures Meta reported after the fact. A refusal
     * that came back in the response body carried its code and this method threw
     * it away, which is why the database could not answer "how many of
     * yesterday's failures were the rate limit?" - the answer was in the reply
     * and was never written down.
     */
    private boolean finish(WhatsappMessageLog row, Attempt attempt) {
        row.setStatus(attempt.sent() ? "SENT" : "FAILED");
        row.setFailureReason(attempt.failureReason());
        row.setFailureCode(attempt.errorCode());
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
            return Attempt.refused("لا يوجد رقم هاتف للمستلم");
        }

        String code = WhatsappResponsibilityCatalog.forOrigin(row.getOrigin());
        Creds creds = instances.resolveFor(code);
        row.setInstanceId(creds.rowId());
        if (!creds.configured()) {
            return Attempt.refused(creds.reasonOrDefault());
        }

        CloudMessageResolver.Resolved template =
                cloudTemplates.forCode(code, vars, creds.phone()).orElse(null);
        if (template == null) {
            return Attempt.refused(needsTemplate(code));
        }
        row.setTemplateName(template.name());
        row.setTemplateCategory(template.category());
        // The history now records the TEMPLATE's own words, filled from the same
        // values the send fills its placeholders from - not a separate copy kept
        // somewhere else. There used to be one, in wa_message_variant, seeded
        // from a hardcoded default and never updated when a template changed. So
        // the teacher read one wording in the log while the parent's phone
        // showed another, and neither of them could tell.
        if (template.text() != null && !template.text().isBlank()) {
            row.setBody(template.text());
        }

        CloudApiClient.HeaderMedia header = null;
        if (document != null && document.length > 0) {
            if (!template.wantsDocument()) {
                return Attempt.refused(
                        "القالب المرتبط بهذا النوع لا يحتوي على رأس ملف، فلا يمكنه حمل المرفق");
            }
            String mediaId = cloudApiClient.uploadMedia(creds.phoneNumberId(), document, fileName,
                    "application/pdf");
            if (mediaId == null) {
                return Attempt.refused("تعذّر رفع الملف إلى واتساب");
            }
            header = new CloudApiClient.HeaderMedia(mediaId, fileName, "document");
        }

        // Pace here, at the single funnel every send passes through, rather than
        // at each call site. Meta enforces two rates - 80 per second overall and
        // one per six seconds to the same recipient - and a path that skipped
        // this would be the one that trips them. Three siblings in a lesson
        // share one parent's phone, which is exactly the shape of a pair-limit
        // violation.
        throttle.acquire(recipient.phone());

        CloudApiClient.SendResult result = cloudApiClient.sendTemplate(creds.phoneNumberId(),
                recipient.phone(),
                new CloudApiClient.TemplateSpec(template.name(), template.language(),
                        template.params(), header, template.headerParam(),
                        template.urlButtonParam()));
        return new Attempt(result.sent(), result.failureReason(), result.messageId(),
                result.errorCode());
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
        return "لا توجد رسالة مثبتة لـ«" + label + "» — اربطها بقالب معتمد من الخدمات ← القوالب";
    }
}
