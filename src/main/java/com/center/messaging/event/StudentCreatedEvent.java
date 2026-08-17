package com.center.messaging.event;

import java.util.UUID;

/**
 * Published once when a student record is first created (desktop/API add or
 * self-signup), so the "new student" WhatsApp message can be sent after the
 * insert commits. Carries the workspace id because the listener runs on another
 * thread with no tenant bound.
 */
public record StudentCreatedEvent(UUID adminId, UUID studentId) {
}
