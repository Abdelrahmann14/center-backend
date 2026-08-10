package com.center.common.enums;
import com.center.center.entity.Center;

/**
 * Stable literals stored in {@code notifications.type}. Free text in the column
 * (no DB check - the set will grow as more of the app sends notifications); this
 * class is the single place the values are defined.
 */
public final class NotificationType {

    /** Sender shown for anything the platform itself sends (not a teacher). */
    public static final String SYSTEM_SENDER = "التطبيق";

    /** A parent asked to link with the recipient student. Deep-links to Parents. */
    public static final String PARENT_LINK_REQUEST = "parent_link_request";
    /** A student approved the recipient parent's link request. */
    public static final String PARENT_LINK_APPROVED = "parent_link_approved";
    /** A student rejected the recipient parent's link request. */
    public static final String PARENT_LINK_REJECTED = "parent_link_rejected";

    /** A new exam was published to the recipient student. Deep-links to the exam. */
    public static final String EXAM_PUBLISHED = "exam_published";
    /** A published exam was edited and re-published: the student must re-download. */
    public static final String EXAM_UPDATED = "exam_updated";
    /** A published exam was deleted: the student's copy and its notifications go away. */
    public static final String EXAM_REMOVED = "exam_removed";
    /** An exam was graded; sent to the student's parents with the result. */
    public static final String EXAM_GRADED = "exam_graded";

    /** An official message broadcast by the super admin from "Center System". */
    public static final String SYSTEM_CENTER = "system_center";
    /** The sender label for super-admin system broadcasts. */
    public static final String CENTER_SENDER = "Center System";

    private NotificationType() {
    }
}
