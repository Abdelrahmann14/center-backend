package com.center.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One inbox entry. {@code linkId} is a deep-link target, null when there is none. */
public record NotificationResponse(
        UUID id,
        String sender,
        /** The sender's profile photo as a base64 data URL, or null when they have none. */
        String senderPhoto,
        String type,
        String title,
        String body,
        UUID linkId,
        boolean read,
        OffsetDateTime createdAt) {
}
