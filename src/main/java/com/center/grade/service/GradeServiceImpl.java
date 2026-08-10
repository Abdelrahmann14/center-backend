package com.center.grade.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.grade.dto.GradeRequest;
import com.center.grade.dto.GradeResponse;
import com.center.grade.entity.Grade;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.grade.mapper.GradeMapper;
import com.center.grade.repository.GradeRepository;
import com.center.grade.service.GradeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService {

    private static final String NOT_FOUND = "الصف غير موجود";
    private static final String DUPLICATE = "يوجد صف بنفس الاسم";

    private final GradeRepository gradeRepository;
    private final GradeMapper gradeMapper;

    @Override
    @Transactional(readOnly = true)
    public List<GradeResponse> findAll() {
        return gradeMapper.toResponses(gradeRepository.findAllByOrderByCreatedAtAsc());
    }

    @Override
    @Transactional
    public GradeResponse create(GradeRequest request) {
        String name = request.name().strip();
        if (gradeRepository.existsByName(name)) {
            throw new DuplicateResourceException(DUPLICATE);
        }
        Grade grade = new Grade();
        apply(grade, request, name);
        return gradeMapper.toResponse(gradeRepository.save(grade));
    }

    @Override
    @Transactional
    public GradeResponse update(UUID gradeId, GradeRequest request) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        String name = request.name().strip();
        if (gradeRepository.existsByNameAndIdNot(name, gradeId)) {
            throw new DuplicateResourceException(DUPLICATE);
        }
        apply(grade, request, name);
        return gradeMapper.toResponse(gradeRepository.save(grade));
    }

    @Override
    @Transactional
    public void delete(UUID gradeId) {
        if (!gradeRepository.existsById(gradeId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        gradeRepository.deleteById(gradeId);
    }

    private static void apply(Grade grade, GradeRequest request, String name) {
        grade.setName(name);
        grade.setTrackKind(request.trackKindOrDefault());
        grade.setActive(request.activeOrDefault());
    }
}
