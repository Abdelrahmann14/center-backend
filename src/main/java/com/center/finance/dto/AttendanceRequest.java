package com.center.finance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Sets the assistants who attended one lesson session, by replacing the whole
 * set: whoever is listed attended, everyone else did not.
 *
 * @param lectureId   the session's lesson
 * @param groupId     its group, or null for a session under no group
 * @param sessionDate the day it was taught
 * @param userIds     the assistants marked present (empty clears the session)
 */
public record AttendanceRequest(
        @NotNull UUID lectureId,
        UUID groupId,
        @NotNull LocalDate sessionDate,
        List<UUID> userIds) {

    public List<UUID> userIdsOrEmpty() {
        return userIds == null ? List.of() : userIds;
    }
}
