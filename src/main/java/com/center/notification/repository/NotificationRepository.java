package com.center.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** One account's inbox, newest first, capped - see {@code NotificationService}. */
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Limit limit);

    /** Unread badge count. */
    long countByRecipientUserIdAndReadFalse(UUID recipientUserId);

    /** A notification only if it belongs to this account - scopes the read action. */
    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    @Modifying
    @Query("update Notification n set n.read = true "
            + "where n.recipientUserId = :userId and n.read = false")
    void markAllRead(@Param("userId") UUID userId);
}
