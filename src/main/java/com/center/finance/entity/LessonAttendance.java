package com.center.finance.entity;

import java.time.LocalDate;
import java.util.UUID;

import com.center.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One assistant marked present at one lesson session.
 *
 * <p>Like {@link FinanceEntry} it points at a session, not a lesson: the key is
 * (lecture, group, date), because the same lesson taught to two groups is two
 * sittings and an assistant attends one of them. A row is the fact "this
 * assistant was there"; the set of rows for a session is its attendance, edited
 * by replacing the whole set.
 */
@Entity
@Table(name = "lesson_attendances", indexes = {
        @Index(name = "lesson_attendances_session_idx", columnList = "admin_id, session_date"),
        @Index(name = "lesson_attendances_lecture_idx", columnList = "admin_id, lecture_id, group_id")
})
@Getter
@Setter
@NoArgsConstructor
public class LessonAttendance extends TenantEntity {

    @Column(name = "lecture_id", nullable = false)
    private UUID lectureId;

    /** Null for a session registered under no group. */
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /** The assistant (a {@code Role.USER} in this admin's workspace). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
