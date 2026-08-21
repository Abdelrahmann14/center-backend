package com.center.messaging.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of the Messages page history table.
 *
 * @param numberLabel  the number it left from, named the way the WhatsApp screens
 *                     name it; null for a send that never reached a number
 * @param templateName the approved template it went out as - what the recipient
 *                     actually read, which is not always what {@code body} says
 */
public record WhatsappMessageLogResponse(
        UUID id,
        String recipientName,
        String phone,
        String recipientCode,
        String recipientType,
        String body,
        String status,
        String failureReason,
        String source,
        String origin,
        String numberLabel,
        String templateName,
        String sentByName,
        OffsetDateTime createdAt) {
}
