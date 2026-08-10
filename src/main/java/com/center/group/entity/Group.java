package com.center.group.entity;
import com.center.common.entity.TenantEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.Formula;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A weekly teaching slot at a center. Unique on (day_of_week, start_time). */
@Entity
@Table(name = "groups",
        uniqueConstraints = @UniqueConstraint(columnNames = {"admin_id", "day_of_week", "start_time"}))
@Getter
@Setter
@NoArgsConstructor
public class Group extends TenantEntity {

    /** 0 = Saturday .. 6 = Friday, matching the UI's day order. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "center_name", nullable = false)
    private String centerName;

    @Column(nullable = false)
    private String grade;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Derived, read-only columns. Each is computed in the same select as the
     * row, so listing groups stays one query instead of three per card.
     */
    @Formula("(select count(*) from students s where s.group_id = id and s.is_active)")
    private long studentCount;

    @Formula("(select max(a.attended_on) from attendance a where a.group_id = id)")
    private LocalDate lastAttendance;

    /**
     * This center's configured price for this group's grade. A @Formula is raw
     * SQL, so Hibernate's tenant filter does NOT reach it - the center is matched
     * by name, and names are only unique per Admin, so the join is pinned to this
     * row's admin_id to avoid picking up another tenant's identically named center.
     */
    @Formula("""
            (select cg.price from center_grades cg
               join centers c on c.id = cg.center_id
              where c.name = center_name and cg.grade = grade and c.admin_id = admin_id)
            """)
    private BigDecimal lessonPrice;
}
