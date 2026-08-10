package com.center.common.entity.converter;

import com.center.common.enums.Role;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleConverter extends PersistableEnumConverter<Role> {

    public RoleConverter() {
        super(Role.class);
    }
}
