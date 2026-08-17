package com.center.messaging.dto;

import java.util.List;

import com.center.common.enums.AutomationType;
import com.center.common.enums.MessageAudience;

/**
 * An automated message type as shown on the Messages page: its switch, audience,
 * the ABSENCE week window, the admin-written base message, and the AI alternatives.
 */
public record AutomationResponse(
        AutomationType type,
        boolean enabled,
        MessageAudience audience,
        Short weekStartDay,
        Short weekEndDay,
        String base,
        boolean baseSendAsImage,
        List<VariantResponse> alternatives) {
}
