package com.center.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Super admin editing a system-message template. Title is null for WhatsApp-only messages. */
public record MessageTemplateUpdateRequest(
        @Size(max = 200) String title,
        @NotBlank(message = "مطلوب") @Size(max = 2000) String body) {
}
