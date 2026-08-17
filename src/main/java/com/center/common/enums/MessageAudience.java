package com.center.common.enums;

/** Who an automated or manual WhatsApp message is delivered to. */
public enum MessageAudience {

    /** The student's own number only. */
    STUDENT,

    /** The parent's number only. */
    PARENT,

    /** Both the student and the parent (two separate messages). */
    BOTH
}
