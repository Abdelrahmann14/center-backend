package com.center.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Completes registration for an existing student (Option 1): the 6-digit code
 * proves phone ownership, and the password creates their login.
 */
public record VerifyExistingRequest(
        @NotNull(message = "مطلوب") @Positive Integer serial,
        @NotBlank(message = "مطلوب") String code,
        @NotBlank(message = "مطلوب") String password,
        @NotBlank(message = "مطلوب") String confirmPassword) {
}
