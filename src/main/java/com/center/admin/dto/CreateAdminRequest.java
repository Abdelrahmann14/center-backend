package com.center.admin.dto;

import com.center.common.constants.ValidationRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Super admin creates a new Admin (teacher), the root of a fresh workspace.
 * {@code username} is the display name; {@code email} is only the part BEFORE
 * the domain - the server appends {@code @center.admin.com}.
 */
public record CreateAdminRequest(
        @NotBlank(message = "مطلوب")
        @Size(max = ValidationRules.USERNAME_MAX) String username,

        @NotBlank(message = "مطلوب") String email,

        /**
         * WhatsApp number, digits only including the country code. Optional here,
         * but the Financials screen has nowhere to send an invoice until it is
         * filled in.
         */
        @Size(max = 20) String phone,

        @NotBlank(message = "مطلوب")
        @Size(min = ValidationRules.PASSWORD_MIN, max = ValidationRules.PASSWORD_MAX)
        String password) {
}
