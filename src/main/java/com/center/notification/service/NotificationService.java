package com.center.notification.service;
import com.center.common.enums.NotificationType;

import java.util.List;
import java.util.UUID;

import com.center.notification.dto.NotificationResponse;

/** The in-app inbox, available to every role. */
public interface NotificationService {

    /**
     * Persists a notification for one recipient. Joins the caller's transaction,
     * so a request that also writes other rows stays atomic.
     *
     * @param sender who it is from - {@code NotificationType.SYSTEM_SENDER} for
     *               platform messages, or a teacher name once teachers send them
     */
    void notify(UUID recipientUserId, String sender, String type, String title, String body, UUID linkId);

    /**
     * Same as {@link #notify}, but tags the row with the broadcast that produced it
     * ({@code outgoingId}) so a deleted broadcast can be pulled from every inbox.
     */
    void notify(UUID recipientUserId, String sender, String type, String title, String body,
            UUID linkId, UUID outgoingId);

    /**
     * Same as {@link #notify}, but records WHICH account sent it, so the inbox can
     * show that person's profile photo beside their name.
     *
     * @param senderUserId the sending account (e.g. the teacher), or null for the platform
     */
    void notifyFrom(UUID recipientUserId, UUID senderUserId, String sender, String type,
            String title, String body, UUID linkId, UUID outgoingId);

    /**
     * Delete every inbox row linked to {@code linkId} whose type is in {@code types}.
     * Used when the linked entity (e.g. an exam) is removed, so its notifications
     * disappear from every recipient's inbox too.
     */
    void deleteByLink(UUID linkId, java.util.Collection<String> types);

    List<NotificationResponse> list(UUID userId);

    long unreadCount(UUID userId);

    /** Marks one notification read - only if it belongs to {@code userId}. */
    void markRead(UUID id, UUID userId);

    void markAllRead(UUID userId);
}
