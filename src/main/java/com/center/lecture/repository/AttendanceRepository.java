package com.center.lecture.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.lecture.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    /**
     * Logs today's attendance for a (group, student) unless already present.
     *
     * <p>Native: a read-then-insert would race two concurrent registrations
     * into duplicate rows; INSERT ... WHERE NOT EXISTS decides it atomically in
     * the database, and JPQL cannot express an insert-from-select.
     */
    @Modifying
    @Query(value = """
            INSERT INTO attendance (group_id, student_id, attended_on, admin_id)
            SELECT :groupId, :studentId, current_date, :adminId
            WHERE NOT EXISTS (
              SELECT 1 FROM attendance
              WHERE group_id = :groupId AND student_id = :studentId
                AND attended_on = current_date
            )
            """, nativeQuery = true)
    void logToday(@Param("groupId") UUID groupId, @Param("studentId") UUID studentId,
            @Param("adminId") UUID adminId);
}
