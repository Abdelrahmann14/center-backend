package com.center.grade.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.center.grade.dto.GradeRequest;
import com.center.grade.dto.GradeResponse;

public interface GradeService {

    List<GradeResponse> findAll();

    /** Only the grades this workspace's centers price, in school order. */
    List<GradeResponse> findInUse();

    /** Grade name to the number of students in it, across every workspace. */
    Map<String, Long> studentCountsByGrade();

    GradeResponse create(GradeRequest request);

    GradeResponse update(UUID gradeId, GradeRequest request);

    void delete(UUID gradeId);
}
