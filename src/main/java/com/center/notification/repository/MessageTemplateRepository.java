package com.center.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.notification.entity.MessageTemplate;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, String> {

    List<MessageTemplate> findAllByOrderByChannelDescCode();

    /** One admin's own custom templates. */
    List<MessageTemplate> findByAdminIdOrderByUpdatedAtDesc(UUID adminId);

    /** The global system templates, shown read-only to admins. */
    List<MessageTemplate> findByAdminIdIsNullAndSystemTrueOrderByCode();

    /** A template by code, scoped to one admin (ownership guard). */
    Optional<MessageTemplate> findByCodeAndAdminId(String code, UUID adminId);
}
