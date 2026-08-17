package com.center.finance.dto;

import java.util.UUID;

/**
 * One assistant as offered in the attendance picker: their name, and whether they
 * are currently marked present for the session being edited.
 */
public record AssistantAttendanceResponse(UUID id, String name, boolean attended) {
}
