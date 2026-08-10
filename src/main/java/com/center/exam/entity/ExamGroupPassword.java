package com.center.exam.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The password one group uses to start a given exam. Regenerated on every publish
 * so each group always gets a fresh, distinct secret. A child of the exam (FK
 * cascade), not a {@code @TenantId} entity: it inherits the exam's workspace.
 */
@Entity
@Table(name = "exam_group_passwords",
        uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "group_id"}),
        indexes = @Index(name = "exam_group_passwords_exam_idx", columnList = "exam_id"))
@Getter
@Setter
@NoArgsConstructor
public class ExamGroupPassword {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private String password;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ExamGroupPassword(UUID examId, UUID groupId, String password) {
        this.examId = examId;
        this.groupId = groupId;
        this.password = password;
        this.updatedAt = OffsetDateTime.now();
    }
}
