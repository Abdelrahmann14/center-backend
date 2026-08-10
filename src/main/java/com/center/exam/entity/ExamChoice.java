package com.center.exam.entity;
import com.center.common.entity.TenantEntity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One answer choice of a question, with a fully custom label and a correct flag. */
@Entity
@Table(name = "exam_choices",
        indexes = @Index(name = "exam_choices_question_idx", columnList = "question_id, position"))
@Getter
@Setter
@NoArgsConstructor
public class ExamChoice extends TenantEntity {

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    /** The label the admin chose: "A"/"B" or "أ"/"ب" or anything else. */
    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private int position;
}
