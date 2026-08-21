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
        /** WhatsApp number invoices are sent to; null until the super admin sets it. */
        String phone,
        /** Public contact number message templates print; null until it is set. */
        String officePhone,
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
        /** Whether the super admin has enabled the WhatsApp numbers feature. */
        boolean whatsappEnabled) {
}
