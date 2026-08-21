package com.center.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One inbox entry. {@code linkId} is a deep-link target, null when there is none. */
public record NotificationResponse(
        UUID id,
        String sender,
        String type,
        String title,
        String body,
        UUID linkId,
        boolean read,
        OffsetDateTime createdAt) {
}
