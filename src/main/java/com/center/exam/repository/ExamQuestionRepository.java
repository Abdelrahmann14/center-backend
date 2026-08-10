package com.center.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.exam.entity.ExamQuestion;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, UUID> {

    List<ExamQuestion> findByExamIdOrderByPositionAsc(UUID examId);

    long countByExamId(UUID examId);

    /** Removes an exam's questions (DB cascades their choices) before a re-save. */
    void deleteByExamId(UUID examId);
}
