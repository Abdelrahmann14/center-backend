package com.center.center.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CenterResponse(
        UUID id,
        String name,
        @JsonProperty("is_active") boolean isActive,
        List<CenterGradePriceResponse> grades) {
}
