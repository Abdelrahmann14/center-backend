package com.center.sync.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Per-mutation outcomes, in request order. */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SyncPushResponse(List<SyncMutationResult> results) {
}
