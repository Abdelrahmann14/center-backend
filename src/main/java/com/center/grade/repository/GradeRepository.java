package com.center.grade.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.grade.entity.Grade;

public interface GradeRepository extends JpaRepository<Grade, UUID> {

    List<Grade> findAllByOrderByCreatedAtAsc();

    Optional<Grade> findByName(String name);

    /** Active grades (tenant-scoped) - the registration grade dropdown. */
    List<Grade> findByActiveTrueOrderByName();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
