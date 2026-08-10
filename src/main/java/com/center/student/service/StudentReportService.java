package com.center.student.service;

import java.util.UUID;

public interface StudentReportService {

    /** The student's analytics report as PDF bytes, ready to download or send. */
    byte[] renderPdf(UUID studentId);

    /** "تقرير - {student name}.pdf", safe for use as a file name. */
    String fileName(UUID studentId);

    /** Who a generated report can be sent to over WhatsApp. */
    enum Recipient { PARENT, STUDENT }

    /**
     * Renders the report and sends it as a WhatsApp document.
     *
     * @return the phone it was delivered to
     */
    String send(UUID studentId, Recipient recipient);
}
