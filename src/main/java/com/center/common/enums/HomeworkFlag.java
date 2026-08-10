package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** registrations.homework_flag. Null means the homework had no issue. */
public enum HomeworkFlag implements PersistableEnum {

    INCOMPLETE("واجب ناقص"),
    NOT_DONE("واجب غير معمول"),
    TRANSFERRED("واجب منقول");

    private final String value;

    HomeworkFlag(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static HomeworkFlag fromValue(String value) {
        return PersistableEnum.fromValue(HomeworkFlag.class, value);
    }
}
