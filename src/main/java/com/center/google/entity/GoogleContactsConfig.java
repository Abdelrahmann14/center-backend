package com.center.google.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-admin Google Contacts sync enable flag, controlled by the super admin from
 * the admin's profile. Not tenant-filtered: the super admin (no tenant) writes it.
 */
@Entity
@Table(name = "google_contacts_config")
@Getter
@Setter
@NoArgsConstructor
public class GoogleContactsConfig {

    @Id
    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public GoogleContactsConfig(UUID adminId, boolean enabled) {
        this.adminId = adminId;
        this.enabled = enabled;
    }
}
