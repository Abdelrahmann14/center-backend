package com.center.common.entity.converter;

import com.center.common.enums.HomeworkFlag;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class HomeworkFlagConverter extends PersistableEnumConverter<HomeworkFlag> {

    public HomeworkFlagConverter() {
        super(HomeworkFlag.class);
    }
}
