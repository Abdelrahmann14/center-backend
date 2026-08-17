package com.center.messaging.dto;

/**
 * How many messages each Lessons-page button would send right now for one
 * (lecture, group): present students not yet messaged, and absent students not
 * yet messaged. Powers the "Send to X people?" confirmation.
 */
public record LecturePendingCounts(int attendance, int absence) {
}
