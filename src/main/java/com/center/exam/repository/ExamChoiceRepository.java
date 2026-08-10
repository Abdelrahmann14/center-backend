package com.center.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.exam.entity.ExamChoice;

public interface ExamChoiceRepository extends JpaRepository<ExamChoice, UUID> {

    List<ExamChoice> findByQuestionIdInOrderByPositionAsc(List<UUID> questionIds);
}
