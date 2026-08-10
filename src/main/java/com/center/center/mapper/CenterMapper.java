package com.center.center.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.center.center.dto.CenterGradePriceResponse;
import com.center.center.dto.CenterResponse;
import com.center.center.entity.Center;
import com.center.center.entity.CenterGrade;

@Mapper
public interface CenterMapper {

    @Mapping(target = "isActive", source = "center.active")
    @Mapping(target = "grades", source = "grades")
    CenterResponse toResponse(Center center, List<CenterGrade> grades);

    @Mapping(target = "grade", source = "id.grade")
    CenterGradePriceResponse toGradePriceResponse(CenterGrade centerGrade);
}
