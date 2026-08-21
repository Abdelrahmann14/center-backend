package com.center.whatsapp.cloud.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.whatsapp.cloud.entity.WhatsappCloudTemplateGrant;
import com.center.whatsapp.cloud.entity.WhatsappCloudTemplateGrantId;

public interface WhatsappCloudTemplateGrantRepository
        extends JpaRepository<WhatsappCloudTemplateGrant, WhatsappCloudTemplateGrantId> {

    List<WhatsappCloudTemplateGrant> findByTemplateId(UUID templateId);

    List<WhatsappCloudTemplateGrant> findByAdminId(UUID adminId);

    void deleteByTemplateId(UUID templateId);
}
