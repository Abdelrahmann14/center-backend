package com.center.notification.dto;

import java.util.List;
import java.util.UUID;

import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.Religion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An admin broadcasting to their OWN students/parents. Every recipient facet is a
 * union (each selection adds matching people), scoped to the admin's workspace.
 */
public record AdminBroadcastRequest(
        List<UUID> studentIds,
        List<UUID> parentIds,
        List<String> grades,
        List<UUID> groupIds,
        List<Gender> genders,
        List<Religion> religions,
        AcademicTrack academicTrack,
        boolean whatsapp,
        @NotBlank(message = "مطلوب") @Size(max = 200) String title,
        @NotBlank(message = "مطلوب") @Size(max = 2000) String body) {
}
