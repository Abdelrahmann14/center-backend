package com.center.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Green API credentials for a new WhatsApp number, entered in the Services page. */
public record WhatsappInstanceRequest(
        @NotBlank(message = "مطلوب") String instanceId,
        @NotBlank(message = "مطلوب") String apiToken,
        @Size(max = 60) String label) {
}
