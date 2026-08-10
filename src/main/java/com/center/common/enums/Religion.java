package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** students.religion - free text in the schema, but a closed set in the UI. */
public enum Religion implements PersistableEnum {

    MUSLIM("مسلم"),
    CHRISTIAN("مسيحي");

    private final String value;

    Religion(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Religion fromValue(String value) {
        return PersistableEnum.fromValue(Religion.class, value);
    }
}
