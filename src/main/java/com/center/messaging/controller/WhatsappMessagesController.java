package com.center.messaging.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.center.auth.security.AuthenticatedUser;
import com.center.common.enums.AutomationType;
import com.center.common.exception.ResourceNotFoundException;
import com.center.messaging.dto.AttendanceOptinRequest;
import com.center.messaging.dto.AttendanceOptinResponse;
import com.center.messaging.dto.AttendanceWhatsappCheck;
import com.center.messaging.dto.AutomationResponse;
import com.center.messaging.dto.AutomationUpdateRequest;
import com.center.messaging.dto.LectureAbsentee;
import com.center.messaging.dto.LectureMessageStatus;
import com.center.messaging.dto.LecturePendingCounts;
import com.center.messaging.dto.VariantResponse;
import com.center.messaging.dto.VariantUpdateRequest;
import com.center.messaging.dto.WhatsappMessageLogResponse;
import com.center.messaging.dto.WhatsappSendRequest;
import com.center.messaging.dto.WhatsappSendResult;
import com.center.messaging.service.WhatsappMessagingService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The Messages page: WhatsApp-only messaging for one workspace. Sending and the
 * history log are gated by the delegatable {@code NOTIFICATION_SEND} permission;
 * configuring the automated messages is admin-only.
 */
@RestController
@RequestMapping("/api/messaging/whatsapp")
@PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Messages")
public class WhatsappMessagesController {

    private final WhatsappMessagingService service;

    // ── Manual send + history (NOTIFICATION_SEND) ─────────────────────────

    @PostMapping("/send")
    public WhatsappSendResult send(@Valid @RequestBody WhatsappSendRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        UUID by = user == null ? null : user.getId();
        String byName = user == null ? null : user.getUsername();
        return service.send(request, by, byName);
    }

    @GetMapping("/log")
    public Page<WhatsappMessageLogResponse> log(Pageable pageable) {
        return service.log(pageable);
    }

    // ── Per-lesson attendance / absence (Lessons page) ────────────────────

    @GetMapping("/lectures/{lectureId}/groups/{groupId}/pending")
    public LecturePendingCounts pending(@PathVariable UUID lectureId, @PathVariable UUID groupId) {
        return service.pendingCounts(lectureId, groupId);
    }

    /** Who has already been messaged about this lesson, per kind (any group). */
    @GetMapping("/lectures/{lectureId}/message-status")
    public LectureMessageStatus messageStatus(@PathVariable UUID lectureId) {
        return service.messageStatus(lectureId);
    }

    /** The group's students who missed this lesson, and who has been told. */
    @GetMapping("/lectures/{lectureId}/groups/{groupId}/absentees")
    public List<LectureAbsentee> absentees(@PathVariable UUID lectureId, @PathVariable UUID groupId) {
        return service.absentees(lectureId, groupId);
    }

    @PostMapping("/lectures/{lectureId}/groups/{groupId}/attendance")
    public WhatsappSendResult sendAttendance(@PathVariable UUID lectureId, @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        UUID by = user == null ? null : user.getId();
        String byName = user == null ? null : user.getUsername();
        return service.sendLectureAttendance(lectureId, groupId, by, byName);
    }

    @PostMapping("/lectures/{lectureId}/groups/{groupId}/absence")
    public WhatsappSendResult sendAbsence(@PathVariable UUID lectureId, @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        UUID by = user == null ? null : user.getId();
        String byName = user == null ? null : user.getUsername();
        return service.sendLectureAbsence(lectureId, groupId, by, byName);
    }

    /** The grades the teacher has finished entering, sent on their say-so. */
    @PostMapping("/lectures/{lectureId}/groups/{groupId}/exam-grade")
    public WhatsappSendResult sendExamGrade(@PathVariable UUID lectureId, @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        UUID by = user == null ? null : user.getId();
        String byName = user == null ? null : user.getUsername();
        return service.sendLectureExamGrade(lectureId, groupId, by, byName);
    }

    @GetMapping("/lectures/{lectureId}/groups/{groupId}/attendance-optin")
    public AttendanceOptinResponse optin(@PathVariable UUID lectureId, @PathVariable UUID groupId) {
        return service.optin(lectureId, groupId);
    }

    /** Pre-registration check: is this student's parent reachable on WhatsApp? */
    @GetMapping("/students/{studentId}/parent-whatsapp")
    public AttendanceWhatsappCheck parentWhatsapp(@PathVariable UUID studentId) {
        return service.parentWhatsappStatus(studentId);
    }

    @PutMapping("/lectures/{lectureId}/groups/{groupId}/attendance-optin")
    public AttendanceOptinResponse setOptin(@PathVariable UUID lectureId, @PathVariable UUID groupId,
            @Valid @RequestBody AttendanceOptinRequest request) {
        return service.setOptin(lectureId, groupId, request.enabled());
    }

    // ── Automated-message config (admin only) ─────────────────────────────

    @GetMapping("/automations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AutomationResponse> automations() {
        return service.automations();
    }

    @PutMapping("/automations/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public AutomationResponse updateAutomation(@PathVariable String type,
            @Valid @RequestBody AutomationUpdateRequest request) {
        return service.updateAutomation(parseType(type), request);
    }

    @PostMapping("/automations/{type}/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public AutomationResponse generate(@PathVariable String type) {
        return service.generateVariants(parseType(type));
    }

    @PutMapping("/automations/variants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public VariantResponse updateVariant(@PathVariable UUID id,
            @Valid @RequestBody VariantUpdateRequest request) {
        return service.updateVariant(id, request);
    }

    private static AutomationType parseType(String type) {
        try {
            return AutomationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("نوع الرسالة غير معروف");
        }
    }
}
