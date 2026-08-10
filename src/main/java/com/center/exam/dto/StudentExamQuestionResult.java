package com.center.exam.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Per-question grading result for the review screen. */
public record StudentExamQuestionResult(
        UUID questionId,
        String text,
        BigDecimal score,
        boolean bonus,
        BigDecimal bonusScore,
        boolean correct,
        BigDecimal awarded,
        List<UUID> selectedChoiceIds,
        List<UUID> correctChoiceIds,
        List<StudentExamChoiceView> choices) {
}
