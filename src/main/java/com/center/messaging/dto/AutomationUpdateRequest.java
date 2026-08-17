package com.center.messaging.dto;

import com.center.common.enums.MessageAudience;

import jakarta.validation.constraints.Size;

/**
 * Saves an automated message's base wording and its "send as image" flag.
 * Attendance/absence are parent-only and are triggered per lesson, so the audience
 * and week fields are accepted for compatibility but no longer used. Alternatives
 * are managed separately (generate / edit).
 */
public record AutomationUpdateRequest(
        Boolean enabled,
        MessageAudience audience,
        Short weekStartDay,
        Short weekEndDay,
        Boolean baseSendAsImage,
        @Size(max = 2000) String base) {
}
