package com.center.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.exam.entity.Exam;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    /** Every live exam in the tenant, ordered so the client can group by stage. */
    List<Exam> findByDeletedAtIsNullOrderByGradeAscCreatedAtAsc();
}
