package com.center.admin.dto;

import com.center.common.constants.ValidationRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Super admin renames an Admin and/or resets its password. A blank/omitted
 * password leaves the current one unchanged.
 */
public record UpdateAdminRequest(
        @NotBlank(message = "مطلوب")
        @Size(max = ValidationRules.USERNAME_MAX) String username,

        @NotBlank(message = "مطلوب") String email,

        /** WhatsApp number, digits only including the country code. */
        @Size(max = 20) String phone,

        /** Public contact number printed inside message templates. */
        @Size(max = 20) String officePhone,

        @Size(min = ValidationRules.PASSWORD_MIN, max = ValidationRules.PASSWORD_MAX)
        String password) {
}
