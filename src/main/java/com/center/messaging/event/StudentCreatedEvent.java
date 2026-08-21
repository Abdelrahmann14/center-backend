package com.center.messaging.event;

import java.util.UUID;

/**
 * Published once when a student record is first created (desktop/API add or
 * self-signup), so the "new student" WhatsApp message can be sent after the
 * insert commits. Carries the workspace id because the listener runs on another
 * thread with no tenant bound.
 *
 * @param createdByUserId the signed-in account that entered the student, which
 *                        is what decides whether the card goes out at all - the
 *                        switch is per account, not per workspace. Null for a
 *                        create with no account behind it (a self-signup, or a
 *                        startup task), and null means nothing is sent: an
 *                        automatic send needs somebody to have asked for it.
 */
public record StudentCreatedEvent(UUID adminId, UUID studentId, UUID createdByUserId) {
}
