package com.center.google.dto;

import java.util.List;
import java.util.UUID;

/**
 * Google Contacts sync status for the current admin.
 *
 * @param enabled    whether the super admin has switched the feature on
 * @param configured whether the platform has Google OAuth credentials at all
 * @param accounts   the admin's connected Google accounts
 */
public record GoogleStatusResponse(
        boolean enabled,
        boolean configured,
        List<Account> accounts) {

    public record Account(UUID id, String email) {}
}
