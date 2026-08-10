package com.center.student.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.student.entity.StudentVerificationCode;

public interface StudentVerificationCodeRepository
        extends JpaRepository<StudentVerificationCode, UUID> {

    /** The newest code issued for a student, whatever its state. */
    Optional<StudentVerificationCode> findFirstByStudentIdOrderByCreatedAtDesc(UUID studentId);

    /** Send-rate guard: how many codes went out for this student recently. */
    long countByStudentIdAndCreatedAtAfter(UUID studentId, OffsetDateTime since);
}
