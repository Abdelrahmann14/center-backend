package com.center.notification.dto;

import java.time.OffsetDateTime;

/** One editable system-message template. */
public record MessageTemplateResponse(
        String code,
        String name,
        String channel,
        String title,
        String body,
        String variables,
        boolean enabled,
        boolean system,
        OffsetDateTime createdAt,
        /** Username of whoever created / last edited the template. */
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {
}
