package com.center.exam.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A question as delivered to a student for taking the exam. */
public record StudentExamQuestionView(
        UUID id,
        String text,
        BigDecimal score,
        boolean allowMultiple,
        boolean bonus,
        BigDecimal bonusScore,
        String note,
        List<StudentExamChoiceView> choices) {
}
