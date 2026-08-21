package com.center.whatsapp.dto;

import java.util.List;

/**
 * Everything a screen needs to decide whether a WhatsApp action can be offered.
 *
 * <p>One endpoint, one answer, computed where the sending happens. The rule this
 * exists to enforce is that the app must never present a button the backend
 * cannot honour: before it, each page decided on its own - "is there a number?" -
 * and got it wrong the moment a number turned out to need an approved template it
 * did not have. A page now asks this and disables what {@code ready} says is not
 * ready, quoting {@code blockedReason} instead of inventing its own wording.
 *
 * @param enabled        whether the super admin turned the feature on here
 * @param connectedCount how many of the workspace's numbers can send right now
 * @param types          every message type with its number and readiness
 */
public record WhatsappAvailabilityResponse(
        boolean enabled,
        int connectedCount,
        List<WhatsappResponsibilityResponse> types) {

    /** Whether anything at all can be sent right now. */
    public boolean anyReady() {
        return types.stream().anyMatch(WhatsappResponsibilityResponse::ready);
    }
}
