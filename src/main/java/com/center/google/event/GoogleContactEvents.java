package com.center.google.event;

import java.util.UUID;

/**
 * Domain events that trigger a Google Contacts re-sync. Published after the
 * relevant DB change and handled after commit on a background thread, so a slow
 * or failing Google call never blocks or rolls back the originating flow.
 */
public final class GoogleContactEvents {

    /** A student was created or changed. */
    public record StudentChanged(UUID adminId, UUID studentId) {}

    /** A Google account was just connected; back-fill every existing student. */
    public record AccountConnected(UUID adminId) {}

    private GoogleContactEvents() {
    }
}
