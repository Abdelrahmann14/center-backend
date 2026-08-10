package com.center.notification.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An editable body for one automatic system message (verification codes,
 * password resets, parent-link confirmations, ...). The super admin edits the
 * title/body; the code interpolates {placeholders} at send time.
 */
@Entity
@Table(name = "message_templates")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class MessageTemplate {

    @Id
    @Column(name = "code", updatable = false)
    private String code;

    /** Owning workspace; null = a global system/super template. */
    @Column(name = "admin_id")
    private UUID adminId;

    @Column(nullable = false)
    private String name;

    /** 'whatsapp' or 'notification'. */
    @Column(nullable = false)
    private String channel;

    /** Null for WhatsApp-only messages (they have a body but no title). */
    private String title;

    @Column(nullable = false)
    private String body;

    /** Comma list of {placeholder} names, shown as an editor hint. */
    private String variables;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Seeded automatic message (editable, not deletable) vs. a custom one. */
    @Column(name = "is_system", nullable = false)
    private boolean system;

    // The seeder inserts rows outside a request, so createdAt keeps its default
    // and the auditor falls back to "system" for the *_by columns.
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
}
