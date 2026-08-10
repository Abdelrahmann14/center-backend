package com.center.exam.controller;
import com.center.student.entity.Student;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.center.exam.dto.StudentExamSubmitRequest;
import com.center.exam.dto.StudentExamDetail;
import com.center.exam.dto.StudentExamResult;
import com.center.exam.dto.StudentExamSummary;
import com.center.auth.security.AuthenticatedUser;
import com.center.exam.service.StudentExamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The student-facing Lesson Exams API. Every endpoint acts as the signed-in student. */
@RestController
@RequestMapping("/api/student/exams")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
@Tag(name = "Student Exams")
public class StudentExamController {

    private final StudentExamService studentExamService;

    @GetMapping
    @Operation(summary = "Published exams available to the signed-in student")
    public List<StudentExamSummary> available(@AuthenticationPrincipal AuthenticatedUser student) {
        return studentExamService.available(student.getId());
    }

    @GetMapping("/{examId}")
    @Operation(summary = "Download one exam to take it (questions, choices, password)")
    public StudentExamDetail open(@PathVariable UUID examId, @AuthenticationPrincipal AuthenticatedUser student) {
        return studentExamService.open(examId, student.getId());
    }

    @PostMapping("/{examId}/submit")
    @Operation(summary = "Submit the exam for authoritative grading")
    public StudentExamResult submit(@PathVariable UUID examId,
            @Valid @RequestBody StudentExamSubmitRequest request,
            @AuthenticationPrincipal AuthenticatedUser student) {
        return studentExamService.submit(examId, student.getId(), request);
    }

    @GetMapping("/{examId}/result")
    @Operation(summary = "The graded result of a submitted attempt (for review)")
    public StudentExamResult result(@PathVariable UUID examId, @AuthenticationPrincipal AuthenticatedUser student) {
        return studentExamService.result(examId, student.getId());
    }
}
