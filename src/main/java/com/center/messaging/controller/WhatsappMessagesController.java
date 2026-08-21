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
import com.center.messaging.dto.AttendanceOptinRequest;
import com.center.messaging.dto.AttendanceOptinResponse;
import com.center.messaging.dto.LectureAbsentee;
import com.center.messaging.dto.LectureMessageStatus;
import com.center.messaging.dto.LecturePendingCounts;
import com.center.messaging.dto.WhatsappMessageLogResponse;
import com.center.messaging.dto.WhatsappSendResult;
import com.center.messaging.service.WhatsappMessagingService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WhatsApp messaging for one workspace: the per-lesson send buttons and the
 * history log, all gated by the delegatable {@code NOTIFICATION_SEND}
 * permission.
 *
 * <p>Nothing here configures the wording. What leaves is an approved Meta
 * template chosen on the platform's own screens, so a teacher has nothing to
 * author and this controller has nothing to save.
 */
@RestController
@RequestMapping("/api/messaging/whatsapp")
@PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Messages")
public class WhatsappMessagesController {

    private final WhatsappMessagingService service;

    // ── History (NOTIFICATION_SEND) ───────────────────────────────────────

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

    @PutMapping("/lectures/{lectureId}/groups/{groupId}/attendance-optin")
    public AttendanceOptinResponse setOptin(@PathVariable UUID lectureId, @PathVariable UUID groupId,
            @Valid @RequestBody AttendanceOptinRequest request) {
        return service.setOptin(lectureId, groupId, request.enabled());
    }

}
