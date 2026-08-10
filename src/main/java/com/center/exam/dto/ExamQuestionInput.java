package com.center.exam.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;

/**
 * One question with its choices in the exam builder. Blank questions are dropped
 * server-side before validation; the score/bonus rules are checked in the service
 * where the exam-level settings are known.
 */
public record ExamQuestionInput(
        String text,
        BigDecimal score,
        boolean allowMultiple,
        boolean bonus,
        BigDecimal bonusScore,
        String note,
        @Valid List<ExamChoiceInput> choices) {
}
