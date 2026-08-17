package com.center.student.service;

import java.util.UUID;

/**
 * The student's identity card: their basic details plus a Code128 barcode of
 * their student code (serial). No lesson, attendance or exam data - this is only
 * the card used to scan them into a lesson quickly.
 */
public interface StudentBarcodeService {

    /** The card rendered to a PDF. */
    byte[] renderPdf(UUID studentId);

    /** The PDF's download name, after the student. */
    String fileName(UUID studentId);

    /**
     * Sends the card to the student's own WhatsApp number.
     *
     * @return the phone it was sent to, for the confirmation message
     */
    String send(UUID studentId);
}
