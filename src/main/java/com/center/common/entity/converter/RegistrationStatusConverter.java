package com.center.common.entity.converter;

import com.center.common.enums.RegistrationStatus;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RegistrationStatusConverter extends PersistableEnumConverter<RegistrationStatus> {

    public RegistrationStatusConverter() {
        super(RegistrationStatus.class);
    }
}
