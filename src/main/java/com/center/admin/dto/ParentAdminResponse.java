package com.center.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One parent row in the super admin's directory table (cross-tenant). */
public record ParentAdminResponse(
        UUID id,
        String name,
        String phone,
        Integer serial,
        boolean active,
        /** Number of students linked to this parent. */
        long studentCount,
        /** Parents only exist after signing up, so this is always true. */
        boolean registered,
        OffsetDateTime createdAt,
        /** Username of whoever created / last edited the row. */
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {
}
