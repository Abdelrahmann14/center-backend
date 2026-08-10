package com.center.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The full exam, including its questions and choices, for the builder. */
public record ExamDetailResponse(
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
        List<ExamQuestionResponse> questions) {
}
