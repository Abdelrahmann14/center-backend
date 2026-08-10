package com.center.student.dto;

import java.util.UUID;

import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.Religion;

/**
 * Query filters for the paginated student list. Every field is optional; nulls
 * simply widen the result.
 */
public record StudentFilter(
        /** Broad box: matches name, school, city, phones, or serial prefix. */
        String search,
        /** Serial prefix only - the lesson-registration code lookup, no phones. */
        String serial,
        /** Name only - no school/city/phone noise. */
        String name,
        /** Student or parent phone, matched anywhere in the number. */
        String phone,
        String grade,
        UUID groupId,
        Gender gender,
        AcademicTrack academicTrack,
        Boolean active,
        Religion religion) {
}
