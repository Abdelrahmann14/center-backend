package com.center.grade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.center.common.constants.ValidationRules;
import com.center.common.enums.TrackKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GradeRequest(
        @NotBlank(message = "مطلوب") @Size(max = ValidationRules.GRADE_NAME_MAX) String name,
        TrackKind trackKind,
        @JsonProperty("is_active") Boolean isActive,
        Integer sortOrder) {

    public TrackKind trackKindOrDefault() {
        return trackKind == null ? TrackKind.NONE : trackKind;
    }

    public boolean activeOrDefault() {
        return isActive == null || isActive;
    }

    /** A grade whose place was not stated goes last, not into the middle. */
    public int sortOrderOrDefault() {
        return sortOrder == null ? 100 : sortOrder;
    }
}
