package com.center.lecture.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import com.center.lecture.dto.LectureFilter;
import com.center.lecture.dto.LectureRequest;
import com.center.analytics.dto.GradeCountResponse;
import com.center.lecture.dto.LectureResponse;

public interface LectureService {

    Page<LectureResponse> search(LectureFilter filter, Pageable pageable);

    /** Lesson counts per grade, for the filter tabs. */
    List<GradeCountResponse> gradeCounts();

    LectureResponse findById(UUID lectureId);

    LectureResponse create(LectureRequest request);

    LectureResponse update(UUID lectureId, LectureRequest request);

    /**
     * Create or update under an id the CLIENT chose, for replaying a write made
     * offline.
     */
    LectureResponse upsert(UUID lectureId, LectureRequest request);

    void delete(UUID lectureId);
}
