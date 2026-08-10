package com.center.account.dto;

import jakarta.validation.constraints.NotBlank;

/** Changing one's own password from the account page - any role. */
public record ChangePasswordRequest(
        @NotBlank(message = "مطلوب") String currentPassword,
        @NotBlank(message = "مطلوب") String newPassword,
        @NotBlank(message = "مطلوب") String confirmPassword) {
}
