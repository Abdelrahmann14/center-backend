package com.center.exam.entity;
import com.center.grade.entity.Grade;
import com.center.common.entity.TenantEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-authored exam linked to one lesson. Its name and max score mirror the
 * lesson's {@code exam_name}/{@code exam_grade}; the grade/stage is copied from
 * the lesson at creation. Scheduling sets a date and the assigned groups.
 */
@Entity
@Table(name = "exams", indexes = {
        @Index(name = "exams_admin_grade_idx", columnList = "admin_id, grade"),
        @Index(name = "exams_lecture_idx", columnList = "lecture_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Exam extends TenantEntity {

    @Column(name = "lecture_id", nullable = false, updatable = false)
    private UUID lectureId;

    @Column(nullable = false)
    private String name;

    /** Stage name, copied from the linked lesson (free-text, matches Grade.name). */
    private String grade;

    @Column(name = "max_score")
    private BigDecimal maxScore;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 30;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "group_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] groupIds = new UUID[0];

    /** Answer label style applied to every question: "arabic" (أ ب ج) or "english" (A B C). */
    @Column(name = "label_style", nullable = false)
    private String labelStyle = "arabic";

    /** When true, individual questions may accept more than one correct choice. */
    @Column(name = "allow_multiple_correct", nullable = false)
    private boolean allowMultipleCorrect = false;

    /** When true, individual questions may carry a note shown above them. */
    @Column(name = "notes_enabled", nullable = false)
    private boolean notesEnabled = false;

    /** When true, individual questions may be flagged as bonus (scored separately). */
    @Column(name = "bonus_enabled", nullable = false)
    private boolean bonusEnabled = false;

    /** Publishable state: true once every validation passes. Recomputed on save/edit. */
    @Column(nullable = false)
    private boolean complete = false;

    /**
     * Legacy single exam password, kept for back-compat. Per-group passwords now
     * live in {@code exam_group_passwords} and are regenerated on every publish.
     */
    @Column(name = "exam_password")
    private String examPassword;

    /** Bumped on every content edit (questions/settings/schedule). Starts at 1. */
    @Column(name = "content_version", nullable = false)
    private int contentVersion = 1;

    /**
     * The {@code contentVersion} captured at the last publish - i.e. the version
     * currently live to students. A downloaded copy below this is outdated. Null
     * until the exam is first published.
     */
    @Column(name = "published_version")
    private Integer publishedVersion;

    /** Set when the exam is published to students; null while still a draft/unpublished. */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /** Soft-delete tombstone for the offline sync follow-up; unused online. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
