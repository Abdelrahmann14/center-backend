package com.center.registration.entity;
import com.center.group.entity.Group;
import com.center.lecture.entity.Lecture;
import com.center.student.entity.Student;
import com.center.common.entity.TenantEntity;

import java.math.BigDecimal;

import org.hibernate.annotations.Formula;

import com.center.common.enums.HomeworkFlag;
import com.center.common.enums.RegistrationStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One student's record for one lesson. */
@Entity
@Table(name = "registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lecture_id", "student_id"}),
        indexes = {
                @Index(name = "registrations_lecture_idx", columnList = "lecture_id"),
                @Index(name = "registrations_student_idx", columnList = "student_id"),
                @Index(name = "registrations_group_idx", columnList = "group_id")
        })
// This table named its author column registered_by long before auditing existed.
@AttributeOverride(name = "createdBy", column = @Column(name = "registered_by", updatable = false))
@Getter
@Setter
@NoArgsConstructor
public class Registration extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** The group registered under - may differ from the student's own group. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(nullable = false)
    private RegistrationStatus status = RegistrationStatus.PRESENT;

    /** Null means not examined. */
    @Column(name = "exam_score")
    private BigDecimal examScore;

    /** Null means the homework had no issue. */
    @Column(name = "homework_flag")
    private HomeworkFlag homeworkFlag;

    /**
     * How many lessons this student has attended overall. Read-only and
     * computed in the same select as the row, so listing a page of
     * registrations stays a single query.
     */
    @Formula("(select count(*) from registrations r2 where r2.student_id = student_id)")
    private long totalLessons;
}
