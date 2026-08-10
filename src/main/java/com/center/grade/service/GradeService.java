package com.center.grade.service;

import java.util.List;
import java.util.UUID;

import com.center.grade.dto.GradeRequest;
import com.center.grade.dto.GradeResponse;

public interface GradeService {

    List<GradeResponse> findAll();

    GradeResponse create(GradeRequest request);

    GradeResponse update(UUID gradeId, GradeRequest request);

    void delete(UUID gradeId);
}
