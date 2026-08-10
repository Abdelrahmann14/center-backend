package com.center.exam.entity;
import com.center.common.entity.TenantEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One student's attempt at one exam. Unique per (tenant, exam, student) so a
 * submission is idempotent and can never be duplicated (also the offline-sync
 * dedup key). Scores are a snapshot taken at grading time.
 */
@Entity
@Table(name = "exam_attempts",
        uniqueConstraints = @UniqueConstraint(name = "exam_attempts_uidx",
                columnNames = {"admin_id", "exam_id", "student_id"}),
        indexes = {
                @Index(name = "exam_attempts_exam_idx", columnList = "exam_id"),
                @Index(name = "exam_attempts_student_idx", columnList = "student_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ExamAttempt extends TenantEntity {

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    /** Achieved regular (non-bonus) score. */
    private BigDecimal score;

    /** Achieved bonus score, tracked independently of the max. */
    @Column(name = "bonus_score")
    private BigDecimal bonusScore;

    /** The exam max score at grading time (snapshot). */
    @Column(name = "max_score")
    private BigDecimal maxScore;

    /** "in_progress" or "submitted". */
    @Column(nullable = false)
    private String status = "in_progress";

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
