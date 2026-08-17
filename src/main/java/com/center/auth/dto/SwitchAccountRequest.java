package com.center.auth.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Switch to another account in the same workspace by confirming its password. */
public record SwitchAccountRequest(
        @NotNull(message = "مطلوب") UUID targetUserId,
        @NotBlank(message = "مطلوب") String password) {
}
