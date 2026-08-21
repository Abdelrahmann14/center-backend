package com.center.whatsapp.service;

import java.util.UUID;

/**
 * Published after every direct WhatsApp send - a text message or a document - so
 * a listener can record it in the message history. These are the sends that do
 * not already go through the messaging log itself (broadcasts and the
 * barcode/report/invoice PDFs).
 *
 * <p>{@code purpose} classifies the message: a responsibility code for a text send
 * (e.g. {@code "exam_result"}) or a document kind ({@code "BARCODE"},
 * {@code "REPORT"}, {@code "INVOICE"}).
 *
 * <p>{@code studentId} is the student the message is ABOUT, when the caller knows
 * it. It is not required - the log resolves the recipient from the number when it
 * is absent - but passing it removes the guesswork for a send about a student
 * whose own number was not the one written to.
 *
 * <p>{@code instanceId}/{@code templateName}/{@code templateCategory} describe the
 * route the message actually took. They exist so the usage dashboard can report
 * per-number volume and cost for these sends too: without them a report PDF would
 * be invisible in the number's own totals, which is precisely the number a teacher
 * looks at first.
 */
public record WhatsappSendEvent(
        String phone,
        String body,
        String purpose,
        boolean sent,
        String failureReason,
        UUID studentId,
        UUID instanceId,
        String templateName,
        String templateCategory) {

    /** A send whose route the caller did not record. */
    public WhatsappSendEvent(String phone, String body, String purpose, boolean sent,
            String failureReason, UUID studentId) {
        this(phone, body, purpose, sent, failureReason, studentId, null, null, null);
    }

    /** A send whose subject the caller could not name; the log resolves it. */
    public WhatsappSendEvent(String phone, String body, String purpose, boolean sent,
            String failureReason) {
        this(phone, body, purpose, sent, failureReason, null);
    }
}
