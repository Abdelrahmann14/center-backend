package com.center.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The custom display name shown as the sender of notifications / WhatsApp messages. */
public record SenderNameRequest(
        @NotBlank(message = "مطلوب") @Size(max = 60) String name) {
}
