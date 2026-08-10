package com.center.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** One account's inbox, newest first. */
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

    /** Unread badge count. */
    long countByRecipientUserIdAndReadFalse(UUID recipientUserId);

    /** A notification only if it belongs to this account - scopes the read action. */
    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    @Modifying
    @Query("update Notification n set n.read = true "
            + "where n.recipientUserId = :userId and n.read = false")
    void markAllRead(@Param("userId") UUID userId);

    /** Remove every per-recipient row produced by one super-admin broadcast. */
    @Modifying
    @Query("delete from Notification n where n.outgoingId = :outgoingId")
    void deleteByOutgoingId(@Param("outgoingId") UUID outgoingId);

    /**
     * Remove the notifications tied to one linked entity, limited to a set of types.
     * Used to pull an exam's publish/update rows from every inbox when it is deleted,
     * without touching unrelated rows that happen to share the link id (e.g. the
     * parents' exam-result notifications).
     */
    @Modifying
    @Query("delete from Notification n where n.linkId = :linkId and n.type in :types")
    void deleteByLinkIdAndTypeIn(@Param("linkId") UUID linkId, @Param("types") java.util.Collection<String> types);
}
