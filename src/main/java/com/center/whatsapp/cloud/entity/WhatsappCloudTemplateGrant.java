package com.center.whatsapp.cloud.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One account allowed to use one template, when that template is not shared with
 * everyone. Consulted only while {@code shared_all} is false - a shared template
 * needs no rows here at all, which is what keeps the common case free.
 */
@Entity
@Table(name = "wa_cloud_template_grant")
@IdClass(WhatsappCloudTemplateGrantId.class)
@Getter
@Setter
@NoArgsConstructor
public class WhatsappCloudTemplateGrant {

    @Id
    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Id
    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    public WhatsappCloudTemplateGrant(UUID templateId, UUID adminId) {
        this.templateId = templateId;
        this.adminId = adminId;
    }
}
