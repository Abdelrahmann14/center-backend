package com.center.messaging.dto;

/** Flips attendance auto-send for a (lecture, group). */
public record AttendanceOptinRequest(boolean enabled) {
}
