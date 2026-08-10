package com.center.whatsapp.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.whatsapp.entity.WhatsappInstance;

public interface WhatsappInstanceRepository extends JpaRepository<WhatsappInstance, UUID> {

    /** The super admin's pool of WhatsApp numbers (null owner), oldest first. */
    List<WhatsappInstance> findByOwnerAdminIdIsNullOrderByCreatedAtAsc();

    /** One admin's pool of WhatsApp numbers, oldest first. */
    List<WhatsappInstance> findByOwnerAdminIdOrderByCreatedAtAsc(UUID ownerAdminId);

    Optional<WhatsappInstance> findByInstanceId(String instanceId);
}
