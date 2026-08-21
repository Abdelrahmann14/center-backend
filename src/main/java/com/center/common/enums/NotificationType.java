package com.center.common.enums;

/**
 * Stable literals stored in {@code notifications.type}. Free text in the column
 * (no DB check - the set will grow as more of the app sends notifications); this
 * class is the single place the values are defined.
 *
 * <p>Only the sender label is left. The exam and guardian-link types were read by
 * the mobile app's own inbox and went with it; what remains in the inbox now is
 * the platform telling a teacher something about their workspace, and those
 * notices carry their own type string at the call site.
 */
public final class NotificationType {

    /** Sender shown for anything the platform itself sends (not a teacher). */
    public static final String SYSTEM_SENDER = "التطبيق";

    private NotificationType() {
    }
}
