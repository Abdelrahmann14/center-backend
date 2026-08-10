package com.center.parent.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A pending parent-link request as the student sees it on Settings -> Parents.
 *
 * @param linkId the parent_student_links id to approve or reject
 */
public record ParentRequestResponse(
        UUID linkId,
        String parentName,
        String phone,
        String email,
        OffsetDateTime requestedAt) {
}
