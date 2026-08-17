package com.center.common.entity.converter;

import com.center.common.enums.FinanceEntryKind;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FinanceEntryKindConverter extends PersistableEnumConverter<FinanceEntryKind> {

    public FinanceEntryKindConverter() {
        super(FinanceEntryKind.class);
    }
}
