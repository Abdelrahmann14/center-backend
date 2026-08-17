package com.center.messaging.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.center.messaging.entity.MessageVariant;

public interface MessageVariantRepository extends JpaRepository<MessageVariant, UUID> {

    /** All wordings of an automation, base (0) first. */
    List<MessageVariant> findByAutomationIdOrderBySortOrder(UUID automationId);

    /** Drops the AI alternatives (keeps the base) before regenerating them. */
    @Modifying
    void deleteByAutomationIdAndSortOrderGreaterThan(UUID automationId, int sortOrder);
}
