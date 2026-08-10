package com.center.sync.dto;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** One authoritative change from the feed. {@link #row} carries snake_case fields. */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SyncEntityChange(
        String entity,
        String op,
        UUID rowId,
        long version,
        Map<String, Object> row) {
}
