package com.center.notification.service;

import java.util.List;
import java.util.UUID;

import com.center.notification.dto.NotificationResponse;

/**
 * The in-app inbox: what the platform tells a signed-in teacher or assistant
 * about their own workspace, read by the bell in the header.
 *
 * <p>It used to also carry teacher-to-student broadcasts, which is why it once
 * knew about senders' photos, deep links and the broadcast a row came from.
 * Those readers were the mobile app's inboxes; what is left is the platform
 * speaking to staff, and it needs none of it.
 */
public interface NotificationService {

    /**
     * Persists a notification for one recipient. Joins the caller's transaction,
     * so a request that also writes other rows stays atomic.
     *
     * @param sender who it is from - {@code NotificationType.SYSTEM_SENDER} for
     *               platform messages, or a teacher name
     */
    void notify(UUID recipientUserId, String sender, String type, String title, String body,
            UUID linkId);

    List<NotificationResponse> list(UUID userId);

    long unreadCount(UUID userId);

    /** Marks one notification read - only if it belongs to {@code userId}. */
    void markRead(UUID id, UUID userId);

    void markAllRead(UUID userId);
}
