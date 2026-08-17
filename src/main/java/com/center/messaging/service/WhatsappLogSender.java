package com.center.messaging.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.center.messaging.entity.WhatsappMessageLog;
import com.center.messaging.repository.WhatsappMessageLogRepository;
import com.center.whatsapp.service.GreenApiClient;

import lombok.RequiredArgsConstructor;

/**
 * Sends one WhatsApp message and records the attempt in {@code wa_message_log}.
 * Every send in the messaging feature goes through here, so the history table sees
 * every message - delivered or failed - with its reason. Must run inside a
 * tenant-bound transaction so the log row is stamped with the workspace.
 */
@Component
@RequiredArgsConstructor
public class WhatsappLogSender {

    /** All messaging sends route through the broadcast number (with failover). */
    private static final String RESPONSIBILITY = "broadcast";
    private static final String IMAGE_NAME = "message.png";

    private final GreenApiClient greenApiClient;
    private final MessageImageRenderer imageRenderer;
    private final WhatsappMessageLogRepository logRepository;

    /** One resolved recipient of a message. */
    public record Recipient(String name, String phone, String code, String type, UUID studentId) {}

    /**
     * Send {@code body} to one recipient and log the result. When {@code asImage} is
     * set the body is rendered to a PNG and sent as a picture; otherwise it is sent
     * as text. {@code lectureId}/{@code groupId} tie the row to a lesson (null for a
     * broadcast). Returns true when the message was accepted by WhatsApp.
     */
    public boolean logAndSend(Recipient recipient, String body, String source, String origin,
            UUID lectureId, UUID groupId, UUID sentByUserId, String sentByName, boolean asImage) {
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

        boolean sent;
        if (recipient.phone() == null || recipient.phone().isBlank()) {
            row.setStatus("FAILED");
            row.setFailureReason("لا يوجد رقم هاتف للمستلم");
            sent = false;
        } else {
            GreenApiClient.SendOutcome outcome = asImage
                    ? greenApiClient.trySendFile(RESPONSIBILITY, recipient.phone(),
                            imageRenderer.render(body), IMAGE_NAME, null)
                    : greenApiClient.trySend(RESPONSIBILITY, recipient.phone(), body);
            sent = outcome.sent();
            row.setStatus(sent ? "SENT" : "FAILED");
            row.setFailureReason(outcome.failureReason());
        }
        logRepository.save(row);
        return sent;
    }

    /**
     * Send {@code caption} to one recipient with {@code document} attached (a PDF
     * card, say), and log the result. Falls back to a plain-text send when no
     * document is available, so a failed render never swallows the message. Used by
     * the new-student flow, which ships the barcode card with the welcome text.
     */
    public boolean logAndSendFile(Recipient recipient, String caption, byte[] document,
            String fileName, String source, String origin) {
        WhatsappMessageLog row = new WhatsappMessageLog();
        row.setRecipientName(recipient.name());
        row.setPhone(recipient.phone());
        row.setRecipientCode(recipient.code());
        row.setRecipientType(recipient.type());
        row.setStudentId(recipient.studentId());
        row.setBody(caption);
        row.setSource(source);
        row.setOrigin(origin);

        boolean sent;
        if (recipient.phone() == null || recipient.phone().isBlank()) {
            row.setStatus("FAILED");
            row.setFailureReason("لا يوجد رقم هاتف للمستلم");
            sent = false;
        } else {
            GreenApiClient.SendOutcome outcome = (document == null || document.length == 0)
                    ? greenApiClient.trySend(RESPONSIBILITY, recipient.phone(), caption)
                    : greenApiClient.trySendFile(RESPONSIBILITY, recipient.phone(), document,
                            fileName == null || fileName.isBlank() ? "file" : fileName, caption);
            sent = outcome.sent();
            row.setStatus(sent ? "SENT" : "FAILED");
            row.setFailureReason(outcome.failureReason());
        }
        logRepository.save(row);
        return sent;
    }
}
