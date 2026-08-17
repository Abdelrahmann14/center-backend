package com.center.messaging.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.common.enums.AutomationType;
import com.center.messaging.entity.MessageAutomation;

public interface MessageAutomationRepository extends JpaRepository<MessageAutomation, UUID> {

    /** The current workspace's config for one type (scoped by @TenantId). */
    Optional<MessageAutomation> findByType(AutomationType type);
}
