package com.center.exam.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.exam.entity.ExamGroupPassword;

public interface ExamGroupPasswordRepository extends JpaRepository<ExamGroupPassword, UUID> {

    List<ExamGroupPassword> findByExamId(UUID examId);

    void deleteByExamId(UUID examId);
}
