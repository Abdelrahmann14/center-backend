package com.center.common.entity.converter;

import com.center.common.enums.PersistableEnum;

import jakarta.persistence.AttributeConverter;

/**
 * Maps a {@link PersistableEnum} to the literal stored in its column, so the
 * concrete converters below stay declarative.
 */
public abstract class PersistableEnumConverter<E extends Enum<E> & PersistableEnum>
        implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected PersistableEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return PersistableEnum.fromValue(type, dbData);
    }
}
