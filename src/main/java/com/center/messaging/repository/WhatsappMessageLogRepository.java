package com.center.messaging.repository;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.messaging.entity.WhatsappMessageLog;

public interface WhatsappMessageLogRepository extends JpaRepository<WhatsappMessageLog, UUID> {

    /** The workspace's send history, newest first (scoped by @TenantId). */
    Page<WhatsappMessageLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Students who already received a delivered message for this lesson + origin. */
    @Query("""
            SELECT DISTINCT m.studentId FROM WhatsappMessageLog m
            WHERE m.lectureId = :lectureId AND m.origin = :origin
              AND m.status = 'SENT' AND m.studentId IS NOT NULL
            """)
    Set<UUID> sentStudentIds(@Param("lectureId") UUID lectureId, @Param("origin") String origin);

    /** Whether one student already got a delivered message for this lesson + origin. */
    boolean existsByStudentIdAndLectureIdAndOriginAndStatus(
            UUID studentId, UUID lectureId, String origin, String status);

    /** Whether one student already got a delivered message for this origin (no lesson scope). */
    boolean existsByStudentIdAndOriginAndStatus(UUID studentId, String origin, String status);
}
