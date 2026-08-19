package com.center.common.enums;

/** The automated WhatsApp message types the system sends on its own. */
public enum AutomationType {

    /** Sent the moment a student's attendance is recorded. */
    ATTENDANCE,

    /** Sent at the end of the configured week to students absent all week. */
    ABSENCE,

    /** Sent once, the moment a new student is added - carries the barcode card.
     *  The same message is re-sent on demand from a student's barcode button. */
    NEW_STUDENT,

    /** Sent when a student's exam grade for a lesson is entered and saved. */
    EXAM_GRADE,

    /** The text sent with a student's report PDF, from the report buttons. */
    REPORT
}
