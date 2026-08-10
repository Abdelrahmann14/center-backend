package com.center.lecture.entity;
import com.center.group.entity.Group;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Group-based attendance log feeding the Groups cards (آخر حضور + counts).
 * Append-only: rows are inserted by lesson registration and never edited.
 */
@Entity
@Table(name = "attendance", indexes = @Index(name = "attendance_group_id_idx", columnList = "group_id"))
@Getter
@Setter
@NoArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owning Admin. Filled on insert and filtered on read by Hibernate. */
    @TenantId
    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "attended_on", nullable = false)
    private LocalDate attendedOn;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
