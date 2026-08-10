package com.center.parent.controller;
import com.center.parent.entity.Parent;
import com.center.student.entity.Student;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.parent.dto.ParentAddStudentRequest;
import com.center.parent.dto.LinkedParentResponse;
import com.center.parent.dto.LinkedStudentResponse;
import com.center.parent.dto.ParentPendingResponse;
import com.center.parent.dto.ParentRequestResponse;
import com.center.auth.security.AuthenticatedUser;
import com.center.parent.service.ParentLinkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Parent<->student linking. The request/approve/reject endpoints are used by the
 * student; add-student and linked-students by the parent.
 */
@RestController
@RequestMapping("/api/parents")
@RequiredArgsConstructor
@Tag(name = "Parents")
public class ParentController {

    private final ParentLinkService parentLinkService;

    // --- Student side ------------------------------------------------------

    @GetMapping("/requests")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Pending parent-link requests for the signed-in student")
    public List<ParentRequestResponse> requests(@AuthenticationPrincipal AuthenticatedUser student) {
        return parentLinkService.pendingRequests(student);
    }

    @PostMapping("/requests/{linkId}/approve")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Approve a parent-link request")
    public void approve(@PathVariable UUID linkId, @AuthenticationPrincipal AuthenticatedUser student) {
        parentLinkService.approve(linkId, student);
    }

    @PostMapping("/requests/{linkId}/reject")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a parent-link request")
    public void reject(@PathVariable UUID linkId, @AuthenticationPrincipal AuthenticatedUser student) {
        parentLinkService.reject(linkId, student);
    }

    @GetMapping("/linked-parents")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Guardians already linked to the signed-in student")
    public List<LinkedParentResponse> linkedParents(@AuthenticationPrincipal AuthenticatedUser student) {
        return parentLinkService.linkedParents(student);
    }

    // --- Parent side -------------------------------------------------------

    @PostMapping("/students")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Request a link to another student (result via in-app notification)")
    public ParentPendingResponse addStudent(@Valid @RequestBody ParentAddStudentRequest request,
            @AuthenticationPrincipal AuthenticatedUser parent) {
        return parentLinkService.addStudent(request.serial(), parent);
    }

    @GetMapping("/linked-students")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Students already linked to the signed-in parent")
    public List<LinkedStudentResponse> linkedStudents(@AuthenticationPrincipal AuthenticatedUser parent) {
        return parentLinkService.linkedStudents(parent);
    }
}
