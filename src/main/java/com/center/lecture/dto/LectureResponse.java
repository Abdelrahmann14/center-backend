package com.center.lecture.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LectureResponse(
        UUID id,
        String name,
        String grade,
        String examName,
        String examGrade,
        /** False = this lesson has no exam; both exam fields are then null. */
        boolean hasExam,
        String homework,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {
}
