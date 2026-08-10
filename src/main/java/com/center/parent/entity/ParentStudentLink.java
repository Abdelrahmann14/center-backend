package com.center.parent.entity;
import com.center.common.entity.BaseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.center.common.enums.LinkStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One parent's request to link with one student, and its outcome. Drives the
 * student's pending-requests screen and both sides' linked lists.
 *
 * <p>Not tenant-scoped: it spans a parent (workspace-less) and a student (in some
 * workspace). {@link #studentAdminId} records the student's workspace so a
 * cross-tenant write - syncing the parent phone onto the student - can rebind to
 * it directly.
 */
@Entity
@Table(name = "parent_student_links",
        uniqueConstraints = @UniqueConstraint(
                name = "parent_student_links_parent_id_student_id_key",
                columnNames = {"parent_id", "student_id"}),
        indexes = {
                @Index(name = "parent_student_links_student_idx", columnList = "student_id, status"),
                @Index(name = "parent_student_links_parent_idx", columnList = "parent_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
public class ParentStudentLink extends BaseEntity {

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "student_admin_id", nullable = false, updatable = false)
    private UUID studentAdminId;

    @Column(nullable = false)
    private LinkStatus status = LinkStatus.PENDING;

    /** The phone the parent supplied - the trusted number once approved. */
    @Column(name = "phone_at_request", nullable = false)
    private String phoneAtRequest;

    /** When the student approved or rejected; null while still pending. */
    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;
}
