package com.center.whatsapp.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.whatsapp.entity.WhatsappInstance;

public interface WhatsappInstanceRepository extends JpaRepository<WhatsappInstance, UUID> {

    /** The platform's own pool of WhatsApp numbers (null owner), oldest first. */
    List<WhatsappInstance> findByOwnerAdminIdIsNullOrderByCreatedAtAsc();

    /** One admin's pool of WhatsApp numbers, oldest first. */
    List<WhatsappInstance> findByOwnerAdminIdOrderByCreatedAtAsc(UUID ownerAdminId);

    /** Every number across all owners - the super admin's platform-wide view. */
    List<WhatsappInstance> findAllByOrderByCreatedAtAsc();

    /** Matches a number by Meta's own id, which is what webhooks carry. */
    Optional<WhatsappInstance> findByPhoneNumberId(String phoneNumberId);
}
