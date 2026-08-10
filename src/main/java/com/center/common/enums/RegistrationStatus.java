package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** registrations.status - constrained by a database check constraint. */
public enum RegistrationStatus implements PersistableEnum {

    PRESENT("present"),
    ABSENT("absent"),
    REMOVED("removed");

    private final String value;

    RegistrationStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RegistrationStatus fromValue(String value) {
        return PersistableEnum.fromValue(RegistrationStatus.class, value);
    }
}
