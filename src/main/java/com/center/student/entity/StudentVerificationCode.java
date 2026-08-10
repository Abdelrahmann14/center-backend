package com.center.student.entity;

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
 * A short-lived 6-digit code sent over WhatsApp so an existing student can prove
 * they own the phone already on file before claiming their record.
 *
 * <p>Deliberately NOT a {@code @TenantId} entity: it is written and read before
 * anyone is authenticated, so no tenant is bound. Each row is already pinned to
 * exactly one student, which is the scope that matters.
 */
@Entity
@Table(name = "student_verification_codes",
        indexes = @Index(name = "student_verification_codes_student_idx",
                columnList = "student_id, created_at desc"))
@Getter
@Setter
@NoArgsConstructor
public class StudentVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(nullable = false)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** Wrong-code tries, so a code cannot be brute-forced within its lifetime. */
    @Column(nullable = false)
    private int attempts;

    /** Single-use: set once the code has completed a registration. */
    @Column(nullable = false)
    private boolean consumed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
