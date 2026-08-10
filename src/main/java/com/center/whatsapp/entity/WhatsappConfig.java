package com.center.whatsapp.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-admin enable flag for the WhatsApp numbers feature, controlled by the super
 * admin from the admin's profile. Not tenant-filtered.
 */
@Entity
@Table(name = "whatsapp_config")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappConfig {

    @Id
    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public WhatsappConfig(UUID adminId, boolean enabled) {
        this.adminId = adminId;
        this.enabled = enabled;
    }
}
