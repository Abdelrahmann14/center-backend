package com.center.whatsapp.service;

import java.util.UUID;

/**
 * Published by {@link GreenApiClient} after every direct WhatsApp send - a text
 * message or a document - so a listener can record it in the message history.
 * These are the sends that do not already go through the messaging log itself
 * (verification codes, exam results, parent-link notices, broadcasts, and the
 * barcode/report/invoice PDFs).
 *
 * <p>{@code purpose} classifies the message: a responsibility code for a text send
 * (e.g. {@code "exam_result"}, {@code "student_verification"}) or a document kind
 * ({@code "BARCODE"}, {@code "REPORT"}, {@code "INVOICE"}).
 *
 * <p>{@code studentId} is the student the message is ABOUT, when the caller knows
 * it. It is not required - the log resolves the recipient from the number when it
 * is absent - but passing it removes the guesswork for a send about a student
 * whose own number was not the one written to.
 */
public record WhatsappSendEvent(
        String phone,
        String body,
        String purpose,
        boolean sent,
        String failureReason,
        UUID studentId) {

    /** A send whose subject the caller could not name; the log resolves it. */
    public WhatsappSendEvent(String phone, String body, String purpose, boolean sent,
            String failureReason) {
        this(phone, body, purpose, sent, failureReason, null);
    }
}
