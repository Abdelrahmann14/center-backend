package com.center.whatsapp.cloud.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

/**
 * Which template carries one message type in one owner scope.
 *
 * @param templateId     the approved template, or null to clear the mapping and
 *                       fall back to the platform's own choice
 * @param urlButtonValue the number a wa.me button should point at; null means
 *                       "whichever number sent the message", which is right
 *                       unless enquiries go to a line that never sends
 */
public record CloudTypeTemplateRequest(
        UUID templateId,
        @Size(max = 30) String urlButtonValue) {
}
