package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** parent_student_links.status - constrained by a database check constraint. */
public enum LinkStatus implements PersistableEnum {

    /** Awaiting the student's decision. */
    PENDING("pending"),
    /** The student approved - the link is live and the parent account is active. */
    APPROVED("approved"),
    /** The student refused the link. */
    REJECTED("rejected");

    private final String value;

    LinkStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LinkStatus fromValue(String value) {
        return PersistableEnum.fromValue(LinkStatus.class, value);
    }
}
