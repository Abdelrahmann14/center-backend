package com.center.common.entity.converter;

import com.center.common.enums.Gender;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GenderConverter extends PersistableEnumConverter<Gender> {

    public GenderConverter() {
        super(Gender.class);
    }
}
