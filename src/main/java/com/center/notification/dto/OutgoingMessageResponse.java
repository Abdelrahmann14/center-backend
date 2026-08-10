package com.center.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One logged super-admin broadcast for the History panel. */
public record OutgoingMessageResponse(
        UUID id,
        String channel,
        String sender,
        String title,
        String body,
        String audience,
        int recipients,
        int whatsappSent,
        OffsetDateTime createdAt) {
}
