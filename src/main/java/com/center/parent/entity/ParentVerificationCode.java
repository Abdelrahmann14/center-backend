package com.center.parent.entity;
import com.center.student.entity.StudentVerificationCode;

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
 * A short-lived 6-digit code sent over WhatsApp so a parent can reset their
 * password. Mirrors {@link StudentVerificationCode}, keyed to a parent.
 */
@Entity
@Table(name = "parent_verification_codes",
        indexes = @Index(name = "parent_verification_codes_parent_idx",
                columnList = "parent_id, created_at desc"))
@Getter
@Setter
@NoArgsConstructor
public class ParentVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    @Column(nullable = false)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean consumed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
