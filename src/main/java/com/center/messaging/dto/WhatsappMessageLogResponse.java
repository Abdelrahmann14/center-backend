package com.center.messaging.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of the Messages page history table. */
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
        String sentByName,
        OffsetDateTime createdAt) {
}
