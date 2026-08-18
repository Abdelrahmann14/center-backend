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
import com.center.common.exception.DuplicateResourceException;
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
    private static final String DUPLICATE_NAME = "يوجد حصة أخرى بنفس الاسم في هذه المرحلة";
    /** Stands in for "no lesson to exclude" on create - never a real id. */
    private static final UUID NO_EXCLUSION = new UUID(0L, 0L);

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
        ensureUniqueName(request, NO_EXCLUSION);
        Lecture lecture = new Lecture();
        apply(lecture, request);
        return lectureMapper.toResponse(lectureRepository.save(lecture));
    }

    @Override
    @Transactional
    public LectureResponse update(UUID lectureId, LectureRequest request) {
        ensureUniqueName(request, lectureId);
        Lecture lecture = findEntity(lectureId);
        apply(lecture, request);
        return lectureMapper.toResponse(lectureRepository.save(lecture));
    }

    /** The offline replay path: same validation, id supplied by the client. */
    @Override
    @Transactional
    public LectureResponse upsert(UUID lectureId, LectureRequest request) {
        ensureUniqueName(request, lectureId);
        Lecture lecture = lectureRepository.findById(lectureId).orElse(null);
        if (lecture == null) {
            lecture = new Lecture();
            lecture.setId(lectureId);
        }
        apply(lecture, request);
        return lectureMapper.toResponse(lectureRepository.save(lecture));
    }

    @Override
    // noRollbackFor: idempotent sync delete catches "already gone"; the RNF is
    // pre-write (see RegistrationServiceImpl.unregister).
    @Transactional(noRollbackFor = ResourceNotFoundException.class)
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

    /** A lesson name may not repeat within one grade (case-insensitive). */
    private void ensureUniqueName(LectureRequest request, UUID excludeId) {
        String grade = TextUtils.blankToNull(request.grade());
        String name = request.name() == null ? "" : request.name().strip();
        if (grade == null || name.isEmpty()) {
            return;
        }
        if (lectureRepository.existsDuplicateName(grade, name, excludeId)) {
            throw new DuplicateResourceException(DUPLICATE_NAME);
        }
    }

    private static void apply(Lecture lecture, LectureRequest request) {
        lecture.setName(request.name().strip());
        lecture.setGrade(TextUtils.blankToNull(request.grade()));
        lecture.setHomework(TextUtils.blankToNull(request.homework()));

        // "بدون اختبار" is a statement about the lesson, so the exam fields are
        // cleared rather than merely ignored: a stale name or maximum left behind
        // would let the screens that read them directly disagree with the flag.
        boolean hasExam = request.hasExamOrDefault();
        lecture.setHasExam(hasExam);
        lecture.setExamName(hasExam ? TextUtils.blankToNull(request.examName()) : null);
        lecture.setExamGrade(hasExam ? TextUtils.blankToNull(request.examGrade()) : null);
    }
}
