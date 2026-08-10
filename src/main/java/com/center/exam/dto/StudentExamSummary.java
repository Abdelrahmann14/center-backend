package com.center.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An available exam in the student's list. {@code status} is one of
 * {@code available}/{@code in_progress}/{@code submitted}; score is present once
 * submitted.
 */
public record StudentExamSummary(
        UUID id,
        String name,
        String grade,
        String lectureName,
        int durationMinutes,
        BigDecimal maxScore,
        LocalDate scheduledDate,
        OffsetDateTime availableUntil,
        String status,
        BigDecimal score,
        BigDecimal bonusScore,
        int version) {
}
