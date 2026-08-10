package com.center.exam.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExamQuestionResponse(
        UUID id,
        String text,
        int position,
        BigDecimal score,
        boolean allowMultiple,
        boolean bonus,
        BigDecimal bonusScore,
        String note,
        List<ExamChoiceResponse> choices) {
}
