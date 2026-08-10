package com.center.parent.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.parent.entity.ParentVerificationCode;

public interface ParentVerificationCodeRepository
        extends JpaRepository<ParentVerificationCode, UUID> {

    Optional<ParentVerificationCode> findFirstByParentIdOrderByCreatedAtDesc(UUID parentId);

    long countByParentIdAndCreatedAtAfter(UUID parentId, OffsetDateTime since);
}
