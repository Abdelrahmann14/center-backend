package com.center.notification.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.notification.entity.OutgoingMessage;

public interface OutgoingMessageRepository extends JpaRepository<OutgoingMessage, UUID> {

    List<OutgoingMessage> findTop30ByOrderByCreatedAtDesc();

    /** One admin's own broadcast history. */
    List<OutgoingMessage> findTop50ByAdminIdOrderByCreatedAtDesc(UUID adminId);
}
