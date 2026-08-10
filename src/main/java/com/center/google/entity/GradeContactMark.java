package com.center.google.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-admin, per-grade Google-contact naming marks. All optional: a null/blank
 * mark means the contact is saved with just the person's name.
 */
@Entity
@Table(name = "grade_contact_mark")
@Getter
@Setter
@NoArgsConstructor
public class GradeContactMark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    /** Suffix when the number is a student's. */
    @Column(name = "student_mark")
    private String studentMark;

    /** Suffix when the number is a parent's. */
    @Column(name = "parent_mark")
    private String parentMark;

    /** Suffix when the same number belongs to both a student and a parent. */
    @Column(name = "both_mark")
    private String bothMark;
}
