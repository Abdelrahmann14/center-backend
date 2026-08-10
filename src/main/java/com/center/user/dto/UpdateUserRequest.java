package com.center.user.dto;

import com.center.common.constants.ValidationRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password is optional - blank leaves the current one untouched. {@code email}
 * is the local part only; the assistant domain is appended server-side.
 */
public record UpdateUserRequest(
        @NotBlank(message = "مطلوب")
        @Size(max = ValidationRules.USERNAME_MAX) String username,

        @NotBlank(message = "مطلوب") String email,

        /** Optional contact number, digits only. */
        @Size(max = 20) String phone,

        @Size(min = ValidationRules.PASSWORD_MIN, max = ValidationRules.PASSWORD_MAX)
        String password) {
}
