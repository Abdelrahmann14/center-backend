package com.center.outbox.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.center.outbox.entity.ExternalEffect;

public interface ExternalEffectRepository extends JpaRepository<ExternalEffect, UUID> {

    Optional<ExternalEffect> findByAdminIdAndKindAndRefId(UUID adminId, String kind, UUID refId);

    List<ExternalEffect> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            OffsetDateTime now, Limit limit);
}
