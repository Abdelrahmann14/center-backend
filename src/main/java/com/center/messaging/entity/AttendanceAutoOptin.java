package com.center.messaging.entity;

import java.util.UUID;

import com.center.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Whether the attendance message auto-sends for one (lecture, group) pairing. Set
 * by the toggle on the registration page; read when a student in that group is
 * marked present. Absent a row, auto-send is off - the teacher can still send the
 * attendance messages later from the Lessons page button.
 */
@Entity
@Table(name = "wa_attendance_optin",
        uniqueConstraints = @UniqueConstraint(columnNames = {"admin_id", "lecture_id", "group_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AttendanceAutoOptin extends TenantEntity {

    @Column(name = "lecture_id", nullable = false)
    private UUID lectureId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private boolean enabled;
}
