package com.center.sync.dto;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * The outcome of one mutation: {@code applied}, {@code duplicate}, {@code conflict}
 * or {@code rejected}. On apply/conflict {@link #row} is the authoritative row.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SyncMutationResult(
        UUID mutationId,
        UUID rowId,
        String outcome,
        Map<String, Object> row,
        Long version,
        String message) {

    public static SyncMutationResult applied(UUID mutationId, UUID rowId, Map<String, Object> row, Long version) {
        return new SyncMutationResult(mutationId, rowId, "applied", row, version, null);
    }

    public static SyncMutationResult duplicate(UUID mutationId, UUID rowId, Map<String, Object> row, Long version) {
        return new SyncMutationResult(mutationId, rowId, "duplicate", row, version, null);
    }

    public static SyncMutationResult conflict(UUID mutationId, UUID rowId, Map<String, Object> row, Long version) {
        return new SyncMutationResult(mutationId, rowId, "conflict", row, version, null);
    }

    public static SyncMutationResult rejected(UUID mutationId, UUID rowId, String message) {
        return new SyncMutationResult(mutationId, rowId, "rejected", null, null, message);
    }
}
