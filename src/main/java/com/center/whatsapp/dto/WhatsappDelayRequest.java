package com.center.whatsapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Sets the number's send delay in whole seconds. Green API allows 500 ms to
 * 600,000 ms; whole seconds from 1 to 600 sit inside that and are what the admin
 * actually needs.
 */
public record WhatsappDelayRequest(
        @NotNull @Min(1) @Max(600) Integer delaySeconds) {
}
