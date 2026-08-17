package com.center.finance.service;

import java.time.LocalDate;
import java.util.UUID;

public interface InvoiceDocumentService {

    /** The PDF file name for a session's invoice, safe for a file system. */
    String fileName(UUID lectureId, UUID groupId, LocalDate sessionDate);

    byte[] renderPdf(UUID lectureId, UUID groupId, LocalDate sessionDate);

    /**
     * Sends the invoice to the workspace owner's WhatsApp number.
     *
     * @return the number it was sent to, for the confirmation message
     */
    String sendToAdmin(UUID lectureId, UUID groupId, LocalDate sessionDate);
}
