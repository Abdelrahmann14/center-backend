package com.center.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Completes registration for an existing student (Option 1): the 6-digit code
 * proves phone ownership, the login name becomes their email and the password
 * creates the account. The display name comes from the existing student record.
 */
public record ClaimExistingRequest(
        @NotNull(message = "مطلوب") @Positive Integer serial,
        @NotBlank(message = "مطلوب") String code,
        /** Login name, WITHOUT the domain; the server appends @center.student.com. */
        @NotBlank(message = "مطلوب") String username,
        @NotBlank(message = "مطلوب") String password,
        @NotBlank(message = "مطلوب") String confirmPassword) {
}
