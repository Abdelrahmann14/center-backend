package com.center.grade.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.center.grade.dto.GradeResponse;
import com.center.grade.entity.Grade;

@Mapper
public interface GradeMapper {

    @Mapping(target = "isActive", source = "active")
    GradeResponse toResponse(Grade grade);

    List<GradeResponse> toResponses(List<Grade> grades);
}
