package com.center.sync.dto;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * One queued offline write, mirroring the {@code Mutation} in {@code @center/core}.
 *
 * <p>The sync envelope is camelCase (matching the shared TS engine), overriding
 * the app's global snake_case strategy; the {@link #payload} map, however, holds
 * entity fields in the usual snake_case ({@code group_id}, {@code attended_on}).
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SyncMutation(
        UUID mutationId,
        String entity,
        String op,
        UUID rowId,
        long baseVersion,
        Map<String, Object> payload,
        String queuedAt) {
}
