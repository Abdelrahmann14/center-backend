package com.center.push.entity;

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
 * An Expo push token for one device. A user may own several (one per device); the
 * token itself is globally unique and re-registers to whichever account last
 * signed in on that device. Not a {@code @TenantId} entity: like a notification it
 * is pinned to a user account, not a workspace.
 */
@Entity
@Table(name = "push_tokens",
        indexes = @Index(name = "push_tokens_user_idx", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column
    private String platform;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
