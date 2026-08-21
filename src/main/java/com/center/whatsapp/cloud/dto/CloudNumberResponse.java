package com.center.whatsapp.cloud.dto;

import java.util.UUID;

/**
 * One number on the official WhatsApp account, as the super admin's screen shows
 * it.
 *
 * @param id                     the instance row id in this system
 * @param label                  internal name, shown in the app only
 * @param ownerAdminId           the teacher the number belongs to, null = platform
 * @param phoneNumberId          Meta's id for the number
 * @param phone                  the number itself, as Meta displays it
 * @param displayName            the name recipients see
 * @param connected              true once registered and able to send
 * @param state                  pending | verified | authorized - how far setup got
 * @param qualityRating          GREEN | YELLOW | RED, from Meta
 * @param codeVerificationStatus VERIFIED | NOT_VERIFIED, from Meta
 */
public record CloudNumberResponse(
        UUID id,
        String label,
        UUID ownerAdminId,
        String phoneNumberId,
        String phone,
        String displayName,
        boolean connected,
        String state,
        String qualityRating,
        String codeVerificationStatus) {
}
