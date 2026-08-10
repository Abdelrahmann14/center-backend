package com.center.lecture.controller;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.lecture.dto.LectureFilter;
import com.center.lecture.dto.LectureRequest;
import com.center.analytics.dto.GradeCountResponse;
import com.center.lecture.dto.LectureResponse;
import com.center.lecture.service.LectureService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
@Tag(name = "Lectures")
public class LectureController {

    private final LectureService lectureService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_LESSON_VIEW','PERM_REGISTRATION_ACCESS','PERM_EXAM_CREATE','PERM_EXAM_UPDATE','PERM_EXAM_DELETE','PERM_EXAM_PUBLISH')")
    @Operation(summary = "Search lessons", description = "Paginated. Filter with search/grade.")
    public Page<LectureResponse> search(
            @ParameterObject LectureFilter filter,
            @ParameterObject @PageableDefault(size = 25, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return lectureService.search(filter, pageable);
    }

    /** Declared before /{lectureId} so "grade-counts" is not read as an id. */
    @GetMapping("/grade-counts")
    @PreAuthorize("hasAnyAuthority('PERM_LESSON_VIEW','PERM_REGISTRATION_ACCESS','PERM_EXAM_CREATE','PERM_EXAM_UPDATE','PERM_EXAM_DELETE','PERM_EXAM_PUBLISH')")
    @Operation(summary = "Lesson counts per grade, for the filter tabs")
    public List<GradeCountResponse> gradeCounts() {
        return lectureService.gradeCounts();
    }

    @GetMapping("/{lectureId}")
    @PreAuthorize("hasAnyAuthority('PERM_LESSON_VIEW','PERM_REGISTRATION_ACCESS','PERM_EXAM_CREATE','PERM_EXAM_UPDATE','PERM_EXAM_DELETE','PERM_EXAM_PUBLISH')")
    public LectureResponse findById(@PathVariable UUID lectureId) {
        return lectureService.findById(lectureId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_LESSON_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public LectureResponse create(@Valid @RequestBody LectureRequest request) {
        return lectureService.create(request);
    }

    @PutMapping("/{lectureId}")
    @PreAuthorize("hasAuthority('PERM_LESSON_UPDATE')")
    public LectureResponse update(@PathVariable UUID lectureId,
            @Valid @RequestBody LectureRequest request) {
        return lectureService.update(lectureId, request);
    }

    @DeleteMapping("/{lectureId}")
    @PreAuthorize("hasAuthority('PERM_LESSON_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID lectureId) {
        lectureService.delete(lectureId);
    }
}
