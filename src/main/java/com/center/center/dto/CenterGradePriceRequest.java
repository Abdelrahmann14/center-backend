package com.center.center.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CenterGradePriceRequest(
        @NotBlank(message = "مطلوب") String grade,
        @NotNull(message = "مطلوب") @PositiveOrZero BigDecimal price) {
}
