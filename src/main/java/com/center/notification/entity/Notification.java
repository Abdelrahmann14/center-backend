package com.center.notification.entity;
import com.center.common.entity.BaseEntity;
import com.center.common.enums.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single in-app notification for one account. Global to every role.
 *
 * <p>Deliberately NOT a {@code @TenantId} entity, and not a {@link BaseEntity}:
 * like {@link StudentVerificationCode} it is a simple append-mostly row (only the
 * read flag flips), pinned to exactly one recipient, and a parent's inbox spans
 * workspaces.
 */
@Entity
@Table(name = "notifications",
        indexes = @Index(name = "notifications_recipient_idx",
                columnList = "recipient_user_id, created_at desc"))
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    /** A stable literal (see {@code NotificationType}) driving the client icon. */
    @Column(nullable = false, updatable = false)
    private String type;

    /** Who it is from - "التطبيق" for system messages, else e.g. a teacher name. */
    @Column(updatable = false)
    private String sender;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private String body;

    /** Optional deep-link target: the row the notification is about. */
    @Column(name = "link_id", updatable = false)
    private UUID linkId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
