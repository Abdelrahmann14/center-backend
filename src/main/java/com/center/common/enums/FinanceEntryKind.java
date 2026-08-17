package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** finance_entries.kind - constrained by a database check constraint. */
public enum FinanceEntryKind implements PersistableEnum {

    /** Added to the invoice total. */
    INCOME("income"),
    /** Deducted from the invoice total. */
    EXPENSE("expense");

    private final String value;

    FinanceEntryKind(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FinanceEntryKind fromValue(String value) {
        return PersistableEnum.fromValue(FinanceEntryKind.class, value);
    }
}
