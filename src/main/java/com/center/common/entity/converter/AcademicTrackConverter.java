package com.center.common.entity.converter;

import com.center.common.enums.AcademicTrack;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AcademicTrackConverter extends PersistableEnumConverter<AcademicTrack> {

    public AcademicTrackConverter() {
        super(AcademicTrack.class);
    }
}
