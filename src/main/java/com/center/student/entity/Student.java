package com.center.student.entity;
import com.center.group.entity.Group;
import com.center.common.entity.TenantEntity;

import java.math.BigDecimal;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.Religion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "students", indexes = {
        @Index(name = "students_group_id_idx", columnList = "group_id"),
        @Index(name = "students_grade_idx", columnList = "grade"),
        @Index(name = "students_is_active_idx", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
public class Student extends TenantEntity {

    /** Sequential student number from a DB sequence - assigned once, never reused. */
    @Generated(event = EventType.INSERT)
    @Column(name = "serial", insertable = false, updatable = false, unique = true)
    private Integer serial;

    @Column(nullable = false)
    private String name;

    private String grade;

    /** Collected at self-registration; null for records created before V22. */
    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    private String school;

    private String city;

    private Gender gender;

    /** The group the student belongs to. Optional - a student may be unassigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "student_phones", nullable = false, columnDefinition = "text[]")
    private String[] studentPhones = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "parent_phones", nullable = false, columnDefinition = "text[]")
    private String[] parentPhones = new String[0];

    private Religion religion;

    @Column(name = "academic_track")
    private AcademicTrack academicTrack;

    /** What this student actually pays; never above the center's grade price. */
    @Column(name = "lesson_price")
    private BigDecimal lessonPrice;

    /** Derived on write: true when lessonPrice is below the center's price. */
    @Column(name = "is_discounted", nullable = false)
    private boolean discounted;

    private String notes;

    /** False = blocked: the record stays, but the student cannot be registered. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Why the student was blocked. Null while they are active. */
    @Column(name = "block_reason")
    private String blockReason;

    /**
     * The login account this student claimed, once they self-register. NULL for
     * a student record created by the teacher that nobody has claimed yet.
     */
    @Column(name = "user_id")
    private java.util.UUID userId;
}
