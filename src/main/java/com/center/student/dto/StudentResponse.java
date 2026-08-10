package com.center.student.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.Religion;

public record StudentResponse(
        UUID id,
        Integer serial,
        String name,
        String grade,
        String school,
        String city,
        Gender gender,
        UUID groupId,
        List<String> studentPhones,
        List<String> parentPhones,
        Religion religion,
        AcademicTrack academicTrack,
        BigDecimal lessonPrice,
        @JsonProperty("is_discounted") boolean isDiscounted,
        String notes,
        @JsonProperty("is_active") boolean isActive,
        /** Why the student is blocked; null while they are active. */
        String blockReason,
        /** Whether the student has claimed a login account (self-registered). */
        boolean registered,
        /** Whether the student's phone(s) are saved as Google contacts. */
        @JsonProperty("google_synced") boolean googleSynced,
        OffsetDateTime createdAt,
        /** Username of whoever created the row (audit columns on BaseEntity). */
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {
}
