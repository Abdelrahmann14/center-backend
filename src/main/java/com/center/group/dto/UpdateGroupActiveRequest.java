package com.center.group.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record UpdateGroupActiveRequest(
        @JsonProperty("is_active") @NotNull(message = "مطلوب") Boolean isActive) {
}
