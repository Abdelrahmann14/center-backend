package com.center.exam.controller;

import java.util.List;
import java.util.UUID;

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

import com.center.exam.dto.ExamBuilderRequest;
import com.center.exam.dto.ExamRequest;
import com.center.exam.dto.ExamScheduleRequest;
import com.center.exam.dto.ExamDetailResponse;
import com.center.exam.dto.ExamResponse;
import com.center.exam.service.ExamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Lesson Exams. Governed by the EXAMS module's fine-grained permissions: an admin
 * holds them all implicitly, and may delegate any of them to an assistant. Reads
 * require any exam permission (exams carry answer keys, so they stay within the
 * module); each mutation requires its specific action.
 */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
@Tag(name = "Exams")
public class ExamController {

    /** Any EXAMS permission grants read access to exam content. */
    private static final String ANY_EXAM = "hasAnyAuthority("
            + "'PERM_EXAM_CREATE','PERM_EXAM_UPDATE','PERM_EXAM_DELETE','PERM_EXAM_PUBLISH')";

    private final ExamService examService;

    @GetMapping
    @PreAuthorize(ANY_EXAM)
    @Operation(summary = "All exams in the workspace (client groups them by stage)")
    public List<ExamResponse> list() {
        return examService.list();
    }

    @GetMapping("/{examId}")
    @PreAuthorize(ANY_EXAM)
    @Operation(summary = "One exam with its questions and choices")
    public ExamDetailResponse get(@PathVariable UUID examId) {
        return examService.get(examId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_EXAM_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an exam for a lesson; writes name/score back to it")
    public ExamResponse create(@Valid @RequestBody ExamRequest request) {
        return examService.create(request);
    }

    @PutMapping("/{examId}")
    @PreAuthorize("hasAuthority('PERM_EXAM_UPDATE')")
    @Operation(summary = "Edit an exam's name/score/duration (written back to the lesson)")
    public ExamResponse update(@PathVariable UUID examId, @Valid @RequestBody ExamRequest request) {
        return examService.update(examId, request);
    }

    @PutMapping("/{examId}/questions")
    @PreAuthorize("hasAuthority('PERM_EXAM_UPDATE')")
    @Operation(summary = "Replace the exam's questions and choices (the builder save)")
    public ExamDetailResponse saveQuestions(@PathVariable UUID examId,
            @Valid @RequestBody ExamBuilderRequest request) {
        return examService.saveQuestions(examId, request);
    }

    @PostMapping("/{examId}/schedule")
    @PreAuthorize("hasAuthority('PERM_EXAM_UPDATE')")
    @Operation(summary = "Assign groups and a date to the exam")
    public ExamResponse schedule(@PathVariable UUID examId,
            @Valid @RequestBody ExamScheduleRequest request) {
        return examService.schedule(examId, request);
    }

    @PostMapping("/{examId}/publish")
    @PreAuthorize("hasAuthority('PERM_EXAM_PUBLISH')")
    @Operation(summary = "Publish a complete, scheduled exam to its students")
    public ExamResponse publish(@PathVariable UUID examId) {
        return examService.publish(examId);
    }

    @DeleteMapping("/{examId}")
    @PreAuthorize("hasAuthority('PERM_EXAM_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an exam and its questions")
    public void delete(@PathVariable UUID examId) {
        examService.delete(examId);
    }
}
