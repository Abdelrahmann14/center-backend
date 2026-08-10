package com.center.lecture.dto;

import com.center.common.constants.ValidationRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LectureRequest(
        @NotBlank(message = "مطلوب") @Size(max = ValidationRules.LECTURE_NAME_MAX) String name,
        String grade,
        String examName,
        String examGrade,
        String homework) {
}
