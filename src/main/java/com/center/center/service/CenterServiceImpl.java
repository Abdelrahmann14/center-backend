package com.center.center.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.center.dto.CenterGradePriceRequest;
import com.center.center.dto.CenterRequest;
import com.center.center.dto.CenterResponse;
import com.center.center.entity.Center;
import com.center.center.entity.CenterGrade;
import com.center.common.exception.DuplicateResourceException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.center.mapper.CenterMapper;
import com.center.center.repository.CenterGradeRepository;
import com.center.center.repository.CenterRepository;
import com.center.center.service.CenterService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CenterServiceImpl implements CenterService {

    private static final String NOT_FOUND = "السنتر غير موجود";
    private static final String DUPLICATE = "يوجد سنتر بنفس الاسم";

    private final CenterRepository centerRepository;
    private final CenterGradeRepository centerGradeRepository;
    private final CenterMapper centerMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CenterResponse> findAll() {
        return centerRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CenterResponse create(CenterRequest request) {
        String name = request.name().strip();
        if (centerRepository.existsByName(name)) {
            throw new DuplicateResourceException(DUPLICATE);
        }
        Center center = new Center();
        center.setName(name);
        center.setActive(request.activeOrDefault());
        Center saved = centerRepository.save(center);
        replaceGrades(saved.getId(), request.gradesOrEmpty());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CenterResponse update(UUID centerId, CenterRequest request) {
        Center center = centerRepository.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        String name = request.name().strip();
        if (centerRepository.existsByNameAndIdNot(name, centerId)) {
            throw new DuplicateResourceException(DUPLICATE);
        }
        center.setName(name);
        center.setActive(request.activeOrDefault());
        Center saved = centerRepository.save(center);
        replaceGrades(centerId, request.gradesOrEmpty());
        return toResponse(saved);
    }

    /**
     * The offline replay path. The name clash still runs and can still refuse:
     * two devices can each add a center called "الرئيسي" with no way to know
     * about the other, and only one of them can keep the name.
     */
    @Override
    @Transactional
    public CenterResponse upsert(UUID centerId, CenterRequest request) {
        String name = request.name().strip();
        if (centerRepository.existsByNameAndIdNot(name, centerId)) {
            throw new DuplicateResourceException(DUPLICATE);
        }
        Center center = centerRepository.findById(centerId).orElse(null);
        if (center == null) {
            center = new Center();
            center.setId(centerId);
        }
        center.setName(name);
        center.setActive(request.activeOrDefault());
        Center saved = centerRepository.save(center);
        replaceGrades(centerId, request.gradesOrEmpty());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID centerId) {
        if (!centerRepository.existsById(centerId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        centerRepository.deleteById(centerId);
    }

    private CenterResponse toResponse(Center center) {
        return centerMapper.toResponse(center,
                centerGradeRepository.findByIdCenterIdOrderByIdGradeAsc(center.getId()));
    }

    /** The form always submits the full price list, so replace wholesale. */
    private void replaceGrades(UUID centerId, List<CenterGradePriceRequest> grades) {
        centerGradeRepository.deleteByIdCenterId(centerId);
        // Flush the deletes before re-inserting, or a resubmitted grade collides
        // with the row still pending removal.
        centerGradeRepository.flush();
        List<CenterGrade> rows = grades.stream()
                .map(g -> new CenterGrade(centerId, g.grade().strip(), g.price(), g.percentageOrZero()))
                .toList();
        centerGradeRepository.saveAll(rows);
        centerGradeRepository.flush();
    }
}
