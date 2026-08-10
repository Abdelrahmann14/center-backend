package com.center.common.entity.converter;

import com.center.common.enums.TrackKind;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrackKindConverter extends PersistableEnumConverter<TrackKind> {

    public TrackKindConverter() {
        super(TrackKind.class);
    }
}
