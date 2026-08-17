package com.center.finance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.finance.entity.LessonAttendance;

public interface LessonAttendanceRepository extends JpaRepository<LessonAttendance, UUID> {

    /** Every attendance row in the window, bucketed by session in the service. */
    List<LessonAttendance> findBySessionDateBetween(LocalDate from, LocalDate to);

    interface UserAttendanceCount {
        UUID getUserId();

        long getCount();
    }

    /** How many sessions each assistant attended, for the Assistants page counter. */
    @Query("SELECT a.userId AS userId, count(a) AS count FROM LessonAttendance a GROUP BY a.userId")
    List<UserAttendanceCount> countByUser();

    /** One assistant's attendance, newest session first, for the detail table. */
    List<LessonAttendance> findByUserIdOrderBySessionDateDescCreatedAtDesc(UUID userId);

    /**
     * One session's rows. The group is part of the key and may be null, which a
     * derived query cannot express (it would emit {@code group_id = null}), so the
     * null case is spelled out.
     */
    @Query("""
            SELECT a FROM LessonAttendance a
            WHERE a.lectureId = :lectureId
              AND a.sessionDate = :sessionDate
              AND ((:groupId IS NULL AND a.groupId IS NULL) OR a.groupId = :groupId)
            """)
    List<LessonAttendance> findForSession(@Param("lectureId") UUID lectureId,
            @Param("groupId") UUID groupId,
            @Param("sessionDate") LocalDate sessionDate);

    /** Clears a session before its attendance is written afresh (replace-set). */
    @Modifying
    @Query("""
            DELETE FROM LessonAttendance a
            WHERE a.lectureId = :lectureId
              AND a.sessionDate = :sessionDate
              AND ((:groupId IS NULL AND a.groupId IS NULL) OR a.groupId = :groupId)
            """)
    void deleteForSession(@Param("lectureId") UUID lectureId,
            @Param("groupId") UUID groupId,
            @Param("sessionDate") LocalDate sessionDate);
}
