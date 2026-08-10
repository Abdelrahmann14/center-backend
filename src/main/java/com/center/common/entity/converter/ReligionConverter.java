package com.center.common.entity.converter;

import com.center.common.enums.Religion;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReligionConverter extends PersistableEnumConverter<Religion> {

    public ReligionConverter() {
        super(Religion.class);
    }
}
