package com.center.registration.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.HomeworkFlag;
import com.center.common.enums.Religion;
import com.center.common.enums.RegistrationStatus;

/**
 * A registration flattened with the student fields the lesson table renders, so
 * the UI never has to load the whole student list to describe the attendees.
 *
 * @param assignedGroupId   the student's own group
 * @param registeredGroupId the group they attended under; differing from
 *                          assignedGroupId is what marks "من مجموعة أخرى"
 * @param totalLessons      lessons this student has attended overall
 * @param attendedAt        when the student was marked present in this lesson
 */
public record RegistrationResponse(
        UUID id,
        UUID studentId,
        Integer serial,
        String name,
        String grade,
        Gender gender,
        String school,
        String city,
        Religion religion,
        AcademicTrack academicTrack,
        BigDecimal lessonPrice,
        List<String> studentPhones,
        List<String> parentPhones,
        @JsonProperty("is_active") boolean isActive,
        UUID assignedGroupId,
        UUID registeredGroupId,
        RegistrationStatus status,
        BigDecimal examScore,
        HomeworkFlag homeworkFlag,
        long totalLessons,
        OffsetDateTime attendedAt) {
}
