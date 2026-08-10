package com.center.exam.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Create/edit an exam. The grade/stage is copied server-side from the linked
 * lesson; the name and max score are written back onto that lesson so the two
 * stay in sync.
 */
public record ExamRequest(
        @NotNull(message = "مطلوب") UUID lectureId,
        @NotBlank(message = "مطلوب") String name,
        @NotNull(message = "مطلوب") @Positive(message = "الدرجة يجب أن تكون أكبر من صفر") BigDecimal maxScore,
        @NotNull(message = "مطلوب") @Positive(message = "المدة يجب أن تكون أكبر من صفر") Integer durationMinutes) {
}
