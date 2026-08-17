package com.center.registration.repository;
import com.center.lecture.entity.Lecture;
import com.center.student.entity.Student;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.registration.entity.Registration;
import com.center.common.enums.Gender;
import com.center.common.enums.HomeworkFlag;
import com.center.common.enums.RegistrationStatus;

public interface RegistrationRepository
        extends JpaRepository<Registration, UUID>, JpaSpecificationExecutor<Registration> {

    /** Fetches the student alongside the row - the response always needs it. */
    @EntityGraph(attributePaths = {"student", "student.group", "group"})
    Optional<Registration> findWithStudentById(UUID id);

    boolean existsByLectureIdAndStudentId(UUID lectureId, UUID studentId);

    /**
     * A student may attend the same lesson again under a DIFFERENT group (a
     * confirmed repeat/makeup), so a duplicate is one that repeats the same
     * group - the registration key is (lecture, student, group).
     */
    boolean existsByLectureIdAndStudentIdAndGroupId(UUID lectureId, UUID studentId, UUID groupId);

    /** The student's record for a lesson, to attach an auto-graded exam score. */
    Optional<Registration> findByLectureIdAndStudentId(UUID lectureId, UUID studentId);

    /** The student's record for a lesson in one specific group (the natural key). */
    Optional<Registration> findByLectureIdAndStudentIdAndGroupId(UUID lectureId, UUID studentId, UUID groupId);

    /** Present students of one (lecture, group), with the student attached to message. */
    @EntityGraph(attributePaths = {"student"})
    List<Registration> findByLectureIdAndGroupIdAndStatus(UUID lectureId, UUID groupId, RegistrationStatus status);

    /** The ids of everyone present at a lesson, in any group - the absence exclusion set. */
    @Query("SELECT r.student.id FROM Registration r WHERE r.lecture.id = :lectureId AND r.status = :status")
    java.util.Set<UUID> presentStudentIds(@Param("lectureId") UUID lectureId,
            @Param("status") RegistrationStatus status);

    interface LessonGroupCount {
        UUID getGroupId();

        long getCount();

        /** When this group sat the lesson - its first attendance row. */
        OffsetDateTime getAttendedAt();
    }

    /** Distinct groups that attended a lesson, with head counts and the date. */
    @Query("""
            SELECT r.group.id AS groupId,
                   count(r) AS count,
                   min(r.attendedAt) AS attendedAt
            FROM Registration r
            WHERE r.lecture.id = :lectureId AND r.status = :status
            GROUP BY r.group.id
            ORDER BY count(r) DESC
            """)
    List<LessonGroupCount> countByGroup(@Param("lectureId") UUID lectureId,
            @Param("status") RegistrationStatus status);

    interface PriceStatRow {
        BigDecimal getPrice();

        Gender getGender();

        UUID getRegisteredGroupId();

        UUID getAssignedGroupId();

        long getTotalLessons();
    }

    /** One row per present student; bucketed by price in the service. */
    @Query("""
            SELECT r.student.lessonPrice  AS price,
                   r.student.gender       AS gender,
                   r.group.id             AS registeredGroupId,
                   r.student.group.id     AS assignedGroupId,
                   r.totalLessons         AS totalLessons
            FROM Registration r
            WHERE r.lecture.id = :lectureId AND r.status = :status
            """)
    List<PriceStatRow> findPriceStats(@Param("lectureId") UUID lectureId,
            @Param("status") RegistrationStatus status);

    interface LessonHistoryRow {
        UUID getId();

        String getLectureName();

        RegistrationStatus getStatus();

        BigDecimal getExamScore();

        /** The lesson's maximum mark, free text ("50", "من 50"). */
        String getExamGrade();

        /** False = the lesson had no exam at all. */
        boolean getHasExam();

        HomeworkFlag getHomeworkFlag();
    }

    /**
     * Every lesson of the student's grade, oldest first by when the lesson was
     * created. A grade lesson with no registration row yields a null status,
     * which the service derives as 'absent'.
     */
    @Query("""
            SELECT l.id           AS id,
                   l.name         AS lectureName,
                   r.status       AS status,
                   r.examScore    AS examScore,
                   l.examGrade    AS examGrade,
                   l.hasExam      AS hasExam,
                   r.homeworkFlag AS homeworkFlag
            FROM Lecture l
            LEFT JOIN Registration r ON r.lecture = l AND r.student.id = :studentId
            WHERE l.grade = (SELECT s.grade FROM Student s WHERE s.id = :studentId)
            ORDER BY l.createdAt ASC, l.id ASC
            """)
    List<LessonHistoryRow> findHistoryForStudent(@Param("studentId") UUID studentId);

    /**
     * The student's own rows, oldest first, with the lesson and group attached -
     * the backbone of the student analytics timeline.
     */
    @Query("""
            SELECT r FROM Registration r
            JOIN FETCH r.lecture
            LEFT JOIN FETCH r.group
            WHERE r.student.id = :studentId
            ORDER BY r.createdAt ASC
            """)
    List<Registration> findStudentHistory(@Param("studentId") UUID studentId);

    interface GroupLessonRow {
        UUID getLectureId();

        String getLectureName();

        /** When the group first registered for this lesson. */
        OffsetDateTime getHeldAt();

        /** False = the lesson had no exam, so a missed lesson is no missed exam. */
        boolean getHasExam();
    }

    /**
     * Lessons the given groups actually held from {@code since} onwards, derived
     * from any student's present row. Used to count what this student missed:
     * a held lesson with no present row of their own.
     */
    @Query("""
            SELECT r.lecture.id   AS lectureId,
                   r.lecture.name AS lectureName,
                   min(r.createdAt) AS heldAt,
                   r.lecture.hasExam AS hasExam
            FROM Registration r
            WHERE r.group.id IN :groupIds
              AND r.status = :status
              AND r.createdAt >= :since
            GROUP BY r.lecture.id, r.lecture.name, r.lecture.hasExam
            """)
    List<GroupLessonRow> findGroupLessonsSince(@Param("groupIds") Collection<UUID> groupIds,
            @Param("status") RegistrationStatus status,
            @Param("since") OffsetDateTime since);

    interface SessionPriceRow {
        UUID getLectureId();

        /** Null for students registered under no group. */
        UUID getGroupId();

        /** When the desk took this registration; the session day is derived from it. */
        OffsetDateTime getRegisteredAt();

        /** Null = the student has no price set. */
        BigDecimal getPrice();
    }

    /**
     * Every present registration in a date window, flat.
     *
     * <p>Deliberately NOT grouped in SQL. A session is (lecture, group, day), and
     * the day would have to come from casting the timestamp - which Postgres does
     * in the DATABASE's timezone. With the server on UTC and the center on Cairo
     * time, a 22:00 lesson would be filed under the next day. Bucketing in Java
     * puts the conversion where the application's zone applies. The window is a
     * week at most, so the row count is small either way.
     */
    @Query("""
            SELECT r.lecture.id          AS lectureId,
                   r.group.id            AS groupId,
                   r.createdAt           AS registeredAt,
                   r.student.lessonPrice AS price
            FROM Registration r
            WHERE r.status = :status
              AND r.createdAt >= :from
              AND r.createdAt < :until
            """)
    List<SessionPriceRow> findSessionPriceRows(@Param("status") RegistrationStatus status,
            @Param("from") OffsetDateTime from,
            @Param("until") OffsetDateTime until);
}
