package com.center.whatsapp.cloud.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.whatsapp.cloud.entity.WhatsappTypeTemplate;
import com.center.whatsapp.cloud.entity.WhatsappTypeTemplateId;

public interface WhatsappTypeTemplateRepository
        extends JpaRepository<WhatsappTypeTemplate, WhatsappTypeTemplateId> {

    List<WhatsappTypeTemplate> findByOwnerAdminId(UUID ownerAdminId);

    void deleteByTemplateId(UUID templateId);
}
