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
 * Which approved template carries one message type, within one owner scope.
 *
 * <p>Same key shape and same sentinel as {@code whatsapp_responsibility}: an
 * all-zero owner is the platform scope, and a teacher with no row of their own
 * falls back to it. That fallback is the point - one approved template written
 * once serves every teacher, and a teacher who needs their own wording gets a
 * row that shadows it.
 */
@Entity
@Table(name = "wa_type_template")
@IdClass(WhatsappTypeTemplateId.class)
@Getter
@Setter
@NoArgsConstructor
public class WhatsappTypeTemplate {

    @Id
    @Column(name = "owner_admin_id", nullable = false)
    private UUID ownerAdminId;

    /** A responsibility code from {@code WhatsappResponsibilityCatalog}. */
    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    /**
     * The number a wa.me button should point at, when the template has one. Null
     * means "the number that sent the message", which is right almost always -
     * this exists for the case where enquiries go to a line that never sends.
     */
    @Column(name = "url_button_value")
    private String urlButtonValue;

    public WhatsappTypeTemplate(UUID ownerAdminId, String code, UUID templateId,
            String urlButtonValue) {
        this.ownerAdminId = ownerAdminId;
        this.code = code;
        this.templateId = templateId;
        this.urlButtonValue = urlButtonValue;
    }
}
