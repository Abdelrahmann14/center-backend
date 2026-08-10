package com.center.google.event;

import java.util.UUID;

/**
 * Domain events that trigger a Google Contacts re-sync. Published after the
 * relevant DB change and handled after commit on a background thread, so a slow
 * or failing Google call never blocks or rolls back the originating flow.
 */
public final class GoogleContactEvents {

    /** A student was created or changed (desktop add, self-registration, edit). */
    public record StudentChanged(UUID adminId, UUID studentId) {}

    /** A parent was created/activated or changed; re-sync their linked students. */
    public record ParentChanged(UUID parentId) {}

    /** A Google account was just connected; back-fill every existing student. */
    public record AccountConnected(UUID adminId) {}

    private GoogleContactEvents() {
    }
}
