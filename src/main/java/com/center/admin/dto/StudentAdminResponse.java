package com.center.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One student row in the super admin's directory table (cross-tenant). */
public record StudentAdminResponse(
        UUID id,
        String name,
        String grade,
        Integer serial,
        boolean active,
        /** The owning teacher's display name. */
        String teacher,
        String phones,
        String parentPhones,
        /** Whether the student has claimed a login account in the app. */
        boolean registered,
        String gender,
        OffsetDateTime createdAt,
        /** Username of whoever created / last edited the row. */
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {
}
