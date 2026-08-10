package com.center.lecture.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @param groupId    null for students registered under no group
 * @param attendedAt when the group sat this lesson - its first attendance row
 */
public record LessonGroupResponse(UUID groupId, long count, OffsetDateTime attendedAt) {
}
