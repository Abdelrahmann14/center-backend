package com.center.parent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Sets a parent's new password after their reset code is verified. Auto-logs in. */
public record ParentResetRequest(
        @NotNull(message = "مطلوب") @Positive Integer parentCode,
        @NotBlank(message = "مطلوب") String code,
        @NotBlank(message = "مطلوب") String password,
        @NotBlank(message = "مطلوب") String confirmPassword) {
}
