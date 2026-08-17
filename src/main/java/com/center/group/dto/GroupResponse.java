package com.center.group.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param startTime      "HH:mm"
 * @param studentCount   active students assigned to this group
 * @param lastAttendance most recent attendance date, null if never attended
 * @param lessonPrice    this center's price for this group's grade
 */
public record GroupResponse(
        UUID id,
        int dayOfWeek,
        String startTime,
        String centerName,
        String grade,
        @JsonProperty("is_active") boolean isActive,
        boolean deleted,
        long studentCount,
        LocalDate lastAttendance,
        BigDecimal lessonPrice) {
}
