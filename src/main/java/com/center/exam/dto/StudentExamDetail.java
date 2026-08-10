package com.center.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The full exam a student downloads to take it. Includes the password and the
 * correct answers so opening, password validation, timing and grading all work
 * offline once downloaded.
 */
public record StudentExamDetail(
        UUID id,
        String name,
        String grade,
        String lectureName,
        int durationMinutes,
        BigDecimal maxScore,
        String labelStyle,
        boolean allowMultipleCorrect,
        boolean bonusEnabled,
        boolean notesEnabled,
        LocalDate scheduledDate,
        OffsetDateTime availableUntil,
        String password,
        String status,
        BigDecimal score,
        BigDecimal bonusScore,
        int version,
        List<StudentExamQuestionView> questions) {
}
