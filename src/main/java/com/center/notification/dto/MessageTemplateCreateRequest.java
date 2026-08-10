package com.center.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Super admin adding a custom message template. */
public record MessageTemplateCreateRequest(
        @NotBlank(message = "مطلوب") @Size(max = 120) String name,
        @NotBlank(message = "مطلوب") @Pattern(regexp = "whatsapp|notification", message = "قناة غير صالحة") String channel,
        @Size(max = 200) String title,
        @NotBlank(message = "مطلوب") @Size(max = 2000) String body) {
}
