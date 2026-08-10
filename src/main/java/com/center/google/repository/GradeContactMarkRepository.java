package com.center.google.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.google.entity.GradeContactMark;

public interface GradeContactMarkRepository extends JpaRepository<GradeContactMark, UUID> {

    List<GradeContactMark> findByAdminId(UUID adminId);

    Optional<GradeContactMark> findByAdminIdAndGradeId(UUID adminId, UUID gradeId);
}
