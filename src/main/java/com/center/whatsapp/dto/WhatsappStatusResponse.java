package com.center.whatsapp.dto;

import java.util.UUID;

/**
 * One connected (or connecting) WhatsApp number in the super admin's pool.
 *
 * @param id         the instance row id
 * @param label      friendly name, or null
 * @param connected  whether the linked phone is authorized and ready
 * @param state      raw Green API state (authorized | notAuthorized | starting | ...)
 * @param phone      the linked phone once authorized, else null
 * @param instanceId the Green API instance id (safe to show)
 */
public record WhatsappStatusResponse(
        UUID id,
        String label,
        boolean connected,
        String state,
        String phone,
        String instanceId) {
}
