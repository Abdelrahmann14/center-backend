package com.center.registration.dto;

import java.util.UUID;

import com.center.common.enums.RegistrationStatus;

/**
 * Query filters for the paginated registration list.
 *
 * @param lectureId  required - registrations are always read per lesson
 * @param groupId    optional group filter, honoured only when {@code groupless}
 *                   is not set
 * @param groupless  when true, returns only rows registered under no group
 *                   (what the old API spelled {@code group_id=none})
 * @param search     matches the student's name, case-insensitively
 */
public record RegistrationFilter(
        UUID lectureId,
        UUID groupId,
        Boolean groupless,
        RegistrationStatus status,
        String search) {

    public boolean isGroupless() {
        return Boolean.TRUE.equals(groupless);
    }
}
