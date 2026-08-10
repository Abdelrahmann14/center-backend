package com.center.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** An exam card: enough to list and show its schedule without opening it. */
public record ExamResponse(
        UUID id,
        UUID lectureId,
        String lectureName,
        String name,
        String grade,
        BigDecimal maxScore,
        int durationMinutes,
        LocalDate scheduledDate,
        List<UUID> groupIds,
        String labelStyle,
        boolean allowMultipleCorrect,
        boolean notesEnabled,
        boolean bonusEnabled,
        boolean complete,
        String examPassword,
        List<GroupPasswordView> groupPasswords,
        boolean published,
        long questionCount) {
}
