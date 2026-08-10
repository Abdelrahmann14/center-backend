package com.center.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Checks a password-reset code WITHOUT consuming it, so the student can be moved
 * to the reset-password step before actually setting a new password.
 */
public record ForgotVerifyRequest(
        @NotNull(message = "مطلوب") @Positive Integer serial,
        @NotBlank(message = "مطلوب") String code) {
}
