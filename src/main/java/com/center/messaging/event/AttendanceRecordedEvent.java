package com.center.messaging.event;

import java.util.UUID;

/**
 * Published when a student's attendance is newly recorded, so the automated
 * attendance message can be sent after the registration commits. Carries the
 * workspace id because the listener runs on another thread with no tenant bound.
 */
public record AttendanceRecordedEvent(UUID adminId, UUID studentId, UUID groupId, UUID lectureId) {
}
