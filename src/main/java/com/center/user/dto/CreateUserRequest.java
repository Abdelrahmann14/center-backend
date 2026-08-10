package com.center.user.dto;

import com.center.common.constants.ValidationRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new assistant. {@code username} is the display name; {@code email} is only
 * the part BEFORE the domain - the server appends {@code @center.assistant.com}.
 */
public record CreateUserRequest(
        @NotBlank(message = "مطلوب")
        @Size(max = ValidationRules.USERNAME_MAX) String username,

        @NotBlank(message = "مطلوب") String email,

        /** Optional contact number, digits only. */
        @Size(max = 20) String phone,

        @NotBlank(message = "مطلوب")
        @Size(min = ValidationRules.PASSWORD_MIN, max = ValidationRules.PASSWORD_MAX)
        String password) {
}
