package com.center.exam.entity;
import com.center.common.entity.TenantEntity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One question in an exam. Choices hang off it. */
@Entity
@Table(name = "exam_questions",
        indexes = @Index(name = "exam_questions_exam_idx", columnList = "exam_id, position"))
@Getter
@Setter
@NoArgsConstructor
public class ExamQuestion extends TenantEntity {

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false)
    private String text;

    /** Display order within the exam. */
    @Column(nullable = false)
    private int position;

    /** This question's share of the exam score (integer or .5 step). */
    @Column(nullable = false)
    private BigDecimal score = BigDecimal.ONE;

    /** When true (and the exam allows it), more than one choice may be correct. */
    @Column(name = "allow_multiple", nullable = false)
    private boolean allowMultiple = false;

    /** Bonus question: scored on its own {@link #bonusScore}, outside the max-score sum. */
    @Column(name = "is_bonus", nullable = false)
    private boolean bonus = false;

    @Column(name = "bonus_score")
    private BigDecimal bonusScore;

    /** Optional note shown above the question for students. */
    private String note;
}
