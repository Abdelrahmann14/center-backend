package com.center.whatsapp.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Flips the workspace's own WhatsApp sending switch.
 *
 * @param enabled true to send, false to pause. {@link NotNull} rather than a
 *                primitive so an omitted field is rejected instead of silently
 *                reading as false - "pause everything" is not a safe default for
 *                a malformed request.
 */
public record WhatsappSendingRequest(@NotNull(message = "مطلوب") Boolean enabled) {
}
