package com.center.finance.dto;

import java.time.LocalDate;

/**
 * One row in an assistant's attendance log: a session they were marked present
 * at, with the lesson and group that identify it.
 */
public record AssistantAttendanceRecordResponse(
        LocalDate sessionDate,
        String lectureName,
        String groupLabel,
        String centerName,
        String grade) {
}
