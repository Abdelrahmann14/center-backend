package com.center.sync.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** One page of the change feed plus the cursor to echo back next time. */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SyncPullResponse(List<SyncEntityChange> changes, String cursor, boolean hasMore) {
}
