package com.center.notification.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A super-admin broadcast. Recipients are the union of every selected facet:
 * {@code categories} (MUSLIMS, CHRISTIANS, MALE_STUDENTS, FEMALE_STUDENTS,
 * ALL_TEACHERS), {@code grades} (student grade strings), one {@code teacherId},
 * and hand-picked {@code userIds}. {@code whatsapp} also sends over WhatsApp
 * wherever a recipient has a phone.
 */
public record BroadcastRequest(
        List<String> categories,
        List<String> grades,
        UUID teacherId,
        /** Send to every registered student of this teacher (their workspace). */
        UUID studentsOfTeacherId,
        List<UUID> userIds,
        boolean whatsapp,
        @NotBlank(message = "مطلوب") @Size(max = 200) String title,
        @NotBlank(message = "مطلوب") @Size(max = 2000) String body) {
}
