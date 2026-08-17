package com.center.student.dto;

import jakarta.validation.constraints.NotBlank;

/** Sets why a discounted student pays below the center's price. */
public record DiscountReasonRequest(
        @NotBlank(message = "مطلوب") String discountReason) {
}
