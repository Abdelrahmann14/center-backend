package com.center.group.dto;

import java.time.LocalTime;
import java.util.UUID;

/** A selectable group for the registration dropdown, scoped to teacher + grade. */
public record GroupOptionResponse(
        UUID id,
        short dayOfWeek,
        LocalTime startTime,
        String centerName) {
}
