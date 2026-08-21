package com.center.whatsapp.cloud.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.whatsapp.cloud.entity.WhatsappCloudTemplate;

public interface WhatsappCloudTemplateRepository extends JpaRepository<WhatsappCloudTemplate, UUID> {

    Optional<WhatsappCloudTemplate> findByMetaTemplateId(String metaTemplateId);

    List<WhatsappCloudTemplate> findAllByOrderByNameAsc();

    /** The ones an automation may actually be mapped to. */
    List<WhatsappCloudTemplate> findByStatusOrderByNameAsc(String status);
}
