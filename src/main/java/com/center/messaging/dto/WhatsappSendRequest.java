package com.center.messaging.dto;

import java.util.List;
import java.util.UUID;

import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.MessageAudience;
import com.center.common.enums.Religion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A manual WhatsApp message an admin (or a permitted user) sends to their own
 * students/parents. Every facet is a union, scoped to the workspace. Unlike the
 * app-notification broadcast this reaches anyone with a phone - no app account
 * needed - and creates no in-app notification.
 */
public record WhatsappSendRequest(
        List<UUID> studentIds,
        List<UUID> parentIds,
        List<String> grades,
        List<UUID> groupIds,
        List<Gender> genders,
        List<Religion> religions,
        AcademicTrack academicTrack,
        /** Whose number the student-derived recipients get the message on. */
        MessageAudience audience,
        @NotBlank(message = "مطلوب") @Size(max = 2000) String body) {
}
