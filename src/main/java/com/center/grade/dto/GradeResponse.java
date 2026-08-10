package com.center.grade.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.center.common.enums.TrackKind;

public record GradeResponse(
        UUID id,
        String name,
        // Pinned: Jackson's boolean "isXxx" convention would otherwise fight the
        // snake_case strategy and emit "active".
        @JsonProperty("is_active") boolean isActive,
        TrackKind trackKind,
        OffsetDateTime createdAt,
        /** Username of whoever created / last edited the row. */
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {
}
