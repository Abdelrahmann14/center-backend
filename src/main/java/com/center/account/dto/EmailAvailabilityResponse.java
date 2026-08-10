package com.center.account.dto;

import java.util.List;

/**
 * Answer to "is this login name free?".
 *
 * @param available whether {@code email} can be taken right now
 * @param email     the full address the local part would become
 * @param valid     whether the typed local part satisfies the character rules
 * @param suggestions free alternatives, only when the name is taken
 */
public record EmailAvailabilityResponse(
        boolean available, String email, boolean valid, List<String> suggestions) {
}
