package com.center.exam.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Schedule an exam: which groups sit it, and on what date. */
public record ExamScheduleRequest(
        @NotEmpty(message = "اختر مجموعة واحدة على الأقل") List<UUID> groupIds,
        @NotNull(message = "مطلوب") LocalDate date) {
}
