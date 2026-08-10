package com.center.whatsapp.dto;

import java.util.UUID;

/**
 * One responsibility from the catalog and which number owns it (null = unassigned).
 *
 * @param code        catalog code
 * @param label       Arabic label
 * @param description what it does
 * @param instanceId  the owning number, or null if unassigned
 */
public record WhatsappResponsibilityResponse(
        String code,
        String label,
        String description,
        UUID instanceId) {
}
