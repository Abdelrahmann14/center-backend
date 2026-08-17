package com.center.registration.controller;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.registration.dto.CreateRegistrationRequest;
import com.center.registration.dto.RegistrationFilter;
import com.center.exam.dto.UpdateExamScoreRequest;
import com.center.registration.dto.UpdateHomeworkRequest;
import com.center.lecture.dto.LessonGroupResponse;
import com.center.lecture.dto.LessonHistoryResponse;
import com.center.analytics.dto.PriceBucketResponse;
import com.center.registration.dto.RegistrationResponse;
import com.center.registration.service.RegistrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
@Tag(name = "Registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Search a lesson's registrations",
            description = "Paginated. Pass groupless=true for students registered under no group.")
    public Page<RegistrationResponse> search(
            @ParameterObject RegistrationFilter filter,
            @ParameterObject @PageableDefault(size = 25, sort = "student.name") Pageable pageable) {
        return registrationService.search(filter, pageable);
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Groups that attended a lesson, with head counts")
    public List<LessonGroupResponse> lessonGroups(@RequestParam("lecture_id") UUID lectureId) {
        return registrationService.lessonGroups(lectureId);
    }

    @GetMapping("/stats-by-price")
    @PreAuthorize("hasAuthority('PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "A lesson's present students aggregated by price paid")
    public List<PriceBucketResponse> statsByPrice(@RequestParam("lecture_id") UUID lectureId) {
        return registrationService.statsByPrice(lectureId);
    }

    @GetMapping("/history/{studentId}")
    @PreAuthorize("hasAuthority('PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Every lesson of the student's grade; unregistered ones read as absent")
    public List<LessonHistoryResponse> history(@PathVariable UUID studentId) {
        return registrationService.historyForStudent(studentId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_REGISTRATION_ACCESS')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a student into a lesson")
    public RegistrationResponse register(@Valid @RequestBody CreateRegistrationRequest request) {
        return registrationService.register(request);
    }

    @PatchMapping("/{registrationId}/homework")
    @PreAuthorize("hasAuthority('PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Set or clear the homework flag")
    public RegistrationResponse updateHomework(@PathVariable UUID registrationId,
            @Valid @RequestBody UpdateHomeworkRequest request) {
        return registrationService.updateHomework(registrationId, request);
    }

    @PatchMapping("/{registrationId}/exam")
    @PreAuthorize("hasAuthority('PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Set or clear the exam score (0..the lesson's maximum)")
    public RegistrationResponse updateExamScore(@PathVariable UUID registrationId,
            @Valid @RequestBody UpdateExamScoreRequest request) {
        return registrationService.updateExamScore(registrationId, request.examScore());
    }

    @DeleteMapping("/{registrationId}")
    @PreAuthorize("hasAuthority('PERM_REGISTRATION_ACCESS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable UUID registrationId) {
        registrationService.unregister(registrationId);
    }
}
