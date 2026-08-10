package com.center.exam.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.exam.entity.ExamAttempt;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    Optional<ExamAttempt> findByExamIdAndStudentId(UUID examId, UUID studentId);

    List<ExamAttempt> findByStudentId(UUID studentId);

    interface LectureScoreRow {
        UUID getLectureId();

        String getExamName();

        BigDecimal getScore();

        BigDecimal getBonusScore();

        BigDecimal getMaxScore();

        String getStatus();
    }

    /**
     * The student's online exam results keyed by the lesson each exam belongs to,
     * so the analytics timeline can show a per-lesson score. Deleted attempts are
     * excluded.
     */
    @Query("""
            SELECT e.lectureId AS lectureId,
                   e.name      AS examName,
                   a.score     AS score,
                   a.bonusScore AS bonusScore,
                   a.maxScore  AS maxScore,
                   a.status    AS status
            FROM ExamAttempt a, Exam e
            WHERE e.id = a.examId
              AND a.studentId = :studentId
              AND a.deletedAt IS NULL
            """)
    List<LectureScoreRow> findLectureScores(@Param("studentId") UUID studentId);
}
