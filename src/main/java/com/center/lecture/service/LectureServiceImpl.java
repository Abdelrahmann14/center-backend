package com.center.lecture.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.lecture.dto.LectureFilter;
import com.center.lecture.dto.LectureRequest;
import com.center.analytics.dto.GradeCountResponse;
import com.center.lecture.dto.LectureResponse;
import com.center.lecture.entity.Lecture;
import com.center.common.exception.ResourceNotFoundException;
import com.center.lecture.mapper.LectureMapper;
import com.center.lecture.repository.LectureRepository;
import com.center.lecture.service.LectureService;
import com.center.lecture.specification.LectureSpecifications;
import com.center.common.util.TextUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureServiceImpl implements LectureService {

    private static final String NOT_FOUND = "الحصة غير موجودة";

    private final LectureRepository lectureRepository;
    private final LectureMapper lectureMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<LectureResponse> search(LectureFilter filter, Pageable pageable) {
        return lectureRepository.findAll(LectureSpecifications.matching(filter), pageable)
                .map(lectureMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GradeCountResponse> gradeCounts() {
        return lectureRepository.countByGrade().stream()
                .map(row -> new GradeCountResponse(row.getGrade(), row.getCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LectureResponse findById(UUID lectureId) {
        return lectureMapper.toResponse(findEntity(lectureId));
    }

    @Override
    @Transactional
    public LectureResponse create(LectureRequest request) {
        Lecture lecture = new Lecture();
        apply(lecture, request);
        return lectureMapper.toResponse(lectureRepository.save(lecture));
    }

    @Override
    @Transactional
    public LectureResponse update(UUID lectureId, LectureRequest request) {
        Lecture lecture = findEntity(lectureId);
        apply(lecture, request);
        return lectureMapper.toResponse(lectureRepository.save(lecture));
    }

    @Override
    @Transactional
    public void delete(UUID lectureId) {
        if (!lectureRepository.existsById(lectureId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        lectureRepository.deleteById(lectureId);
    }

    private Lecture findEntity(UUID lectureId) {
        return lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private static void apply(Lecture lecture, LectureRequest request) {
        lecture.setName(request.name().strip());
        lecture.setGrade(TextUtils.blankToNull(request.grade()));
        lecture.setExamName(TextUtils.blankToNull(request.examName()));
        lecture.setExamGrade(TextUtils.blankToNull(request.examGrade()));
        lecture.setHomework(TextUtils.blankToNull(request.homework()));
    }
}
