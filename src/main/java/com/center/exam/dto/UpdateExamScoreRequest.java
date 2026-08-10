package com.center.exam.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.PositiveOrZero;

/** Null clears the score, meaning the student was not examined. */
public record UpdateExamScoreRequest(
        @PositiveOrZero(message = "الدرجة لا يمكن أن تكون أقل من صفر") BigDecimal examScore) {
}
