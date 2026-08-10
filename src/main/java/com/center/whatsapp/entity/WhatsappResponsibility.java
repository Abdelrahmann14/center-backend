package com.center.whatsapp.entity;

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
 * Which WhatsApp number ({@link WhatsappInstance}) is responsible for one send
 * purpose, within one owner scope. The key is (owner_admin_id, code) so a purpose
 * is owned by at most one number per scope. A zero UUID owner = the super-admin
 * scope; any other value = that admin's scope.
 */
@Entity
@Table(name = "whatsapp_responsibility")
@IdClass(WhatsappResponsibilityId.class)
@Getter
@Setter
@NoArgsConstructor
public class WhatsappResponsibility {

    @Id
    @Column(name = "owner_admin_id", nullable = false)
    private UUID ownerAdminId;

    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    public WhatsappResponsibility(UUID ownerAdminId, String code, UUID instanceId) {
        this.ownerAdminId = ownerAdminId;
        this.code = code;
        this.instanceId = instanceId;
    }
}
