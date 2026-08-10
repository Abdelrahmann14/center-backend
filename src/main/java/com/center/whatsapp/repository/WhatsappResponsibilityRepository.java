package com.center.whatsapp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.whatsapp.entity.WhatsappResponsibility;
import com.center.whatsapp.entity.WhatsappResponsibilityId;

public interface WhatsappResponsibilityRepository
        extends JpaRepository<WhatsappResponsibility, WhatsappResponsibilityId> {

    List<WhatsappResponsibility> findByOwnerAdminId(UUID ownerAdminId);

    List<WhatsappResponsibility> findByInstanceId(UUID instanceId);

    void deleteByInstanceId(UUID instanceId);
}
