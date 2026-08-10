package com.center.admin.dto;
import com.center.parent.dto.LinkedPersonResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Full student profile for the super admin's student detail / edit form. */
public record StudentDetailResponse(
        UUID id,
        String name,
        String grade,
        Integer serial,
        boolean active,
        String teacher,
        String phones,
        String parentPhones,
        UUID userId,
        String photo,
        String gender,
        String religion,
        String academicTrack,
        String school,
        String city,
        LocalDate birthDate,
        BigDecimal lessonPrice,
        boolean discounted,
        String notes,
        List<LinkedPersonResponse> parents) {
}
