package com.center.common.entity.converter;

import com.center.common.enums.LinkStatus;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LinkStatusConverter extends PersistableEnumConverter<LinkStatus> {

    public LinkStatusConverter() {
        super(LinkStatus.class);
    }
}
