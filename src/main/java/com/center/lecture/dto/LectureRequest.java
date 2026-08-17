package com.center.lecture.dto;

import com.center.common.constants.ValidationRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LectureRequest(
        @NotBlank(message = "مطلوب") @Size(max = ValidationRules.LECTURE_NAME_MAX) String name,
        String grade,
        String examName,
        String examGrade,

        /**
         * Null means "has an exam", so an older client (or an offline replay
         * written before the field existed) keeps its previous meaning.
         */
        Boolean hasExam,
        String homework) {

    public boolean hasExamOrDefault() {
        return hasExam == null || hasExam;
    }
}
