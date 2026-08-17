package com.center.center.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param percentage the center's share of this grade's takings, 0-100. Optional
 *                   so an older client (or the offline replay of a center saved
 *                   before the field existed) still validates; null reads as 0.
 */
public record CenterGradePriceRequest(
        @NotBlank(message = "مطلوب") String grade,
        @NotNull(message = "مطلوب") @PositiveOrZero BigDecimal price,
        @PositiveOrZero @DecimalMax(value = "100", message = "النسبة لا تتجاوز 100") BigDecimal percentage) {

    public BigDecimal percentageOrZero() {
        return percentage == null ? BigDecimal.ZERO : percentage;
    }
}
