package com.center.center.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.center.common.constants.ValidationRules;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CenterRequest(
        @NotBlank(message = "مطلوب") @Size(max = ValidationRules.CENTER_NAME_MAX) String name,
        @JsonProperty("is_active") Boolean isActive,
        @Valid List<CenterGradePriceRequest> grades) {

    public boolean activeOrDefault() {
        return isActive == null || isActive;
    }

    public List<CenterGradePriceRequest> gradesOrEmpty() {
        return grades == null ? List.of() : grades;
    }
}
