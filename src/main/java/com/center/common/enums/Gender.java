package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** students.gender - free text in the schema, but a closed set in the UI. */
public enum Gender implements PersistableEnum {

    MALE("ذكر"),
    FEMALE("أنثى");

    private final String value;

    Gender(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Gender fromValue(String value) {
        return PersistableEnum.fromValue(Gender.class, value);
    }
}
