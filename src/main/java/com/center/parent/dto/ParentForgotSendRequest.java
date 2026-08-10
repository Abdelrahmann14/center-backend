package com.center.parent.dto;
import com.center.parent.entity.Parent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Requests a password-reset code for a parent, keyed by their Parent Code. */
public record ParentForgotSendRequest(
        @NotNull(message = "مطلوب") @Positive Integer parentCode) {
}
