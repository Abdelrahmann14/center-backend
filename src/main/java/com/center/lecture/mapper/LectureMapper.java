package com.center.lecture.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.center.lecture.dto.LectureResponse;
import com.center.lecture.entity.Lecture;

@Mapper
public interface LectureMapper {

    LectureResponse toResponse(Lecture lecture);

    List<LectureResponse> toResponses(List<Lecture> lectures);
}
