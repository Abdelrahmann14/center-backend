package com.center.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One Admin (workspace) as the super admin sees it, with live counts. */
public record AdminSummaryResponse(
        UUID id,
        /** Display name. */
        String username,
        /** Login address - {@code <local>@center.admin.com}. */
        String email,
        boolean active,
        OffsetDateTime createdAt,
        /** Username of whoever created / last edited the row. */
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy,
        long studentCount,
        long assistantCount,
        /** Profile photo as a base64 data URL, or null when none set. */
        String photo,
        /** Whether the super admin has enabled Google Contacts sync for this admin. */
        boolean googleSyncEnabled,
        /** Whether the super admin has enabled the WhatsApp numbers feature. */
        boolean whatsappEnabled) {
}
