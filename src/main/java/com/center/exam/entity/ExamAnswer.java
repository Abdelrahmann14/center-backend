package com.center.exam.entity;
import com.center.common.entity.TenantEntity;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One student's answer to one question in an attempt, with the grading result. */
@Entity
@Table(name = "exam_answers",
        uniqueConstraints = @UniqueConstraint(name = "exam_answers_uidx",
                columnNames = {"attempt_id", "question_id"}),
        indexes = @Index(name = "exam_answers_attempt_idx", columnList = "attempt_id"))
@Getter
@Setter
@NoArgsConstructor
public class ExamAnswer extends TenantEntity {

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    /** The choices the student selected (one for single-answer, many for multi). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "choice_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] choiceIds = new UUID[0];

    @Column(nullable = false)
    private boolean correct = false;

    @Column(nullable = false)
    private BigDecimal awarded = BigDecimal.ZERO;
}
