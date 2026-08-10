package com.center.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Requests a WhatsApp verification code for an existing student (Option 1). */
public record SendCodeRequest(
        /** The student's existing serial/code. */
        @NotNull(message = "مطلوب") @Positive Integer serial) {
}
