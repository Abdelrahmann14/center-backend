package com.center.sync.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** A batch of offline writes to apply. */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SyncPushRequest(List<SyncMutation> mutations) {
}
