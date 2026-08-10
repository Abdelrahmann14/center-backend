package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** grades.track_kind - drives which الشعبة options the student form offers. */
public enum TrackKind implements PersistableEnum {

    NONE("none"),
    G11("g11"),
    G12("g12");

    private final String value;

    TrackKind(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TrackKind fromValue(String value) {
        return PersistableEnum.fromValue(TrackKind.class, value);
    }
}
