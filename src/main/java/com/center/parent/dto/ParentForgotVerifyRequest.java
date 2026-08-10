package com.center.parent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Checks a parent's reset code before the reset step. */
public record ParentForgotVerifyRequest(
        @NotNull(message = "مطلوب") @Positive Integer parentCode,
        @NotBlank(message = "مطلوب") String code) {
}
