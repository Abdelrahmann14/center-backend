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

    /**
     * The teacher's own pause switch, separate from {@link #enabled} above.
     *
     * <p>{@code enabled} is the platform's answer to "may this workspace use
     * WhatsApp at all"; this is the workspace's answer to "is it sending right
     * now". A teacher pausing their own sending must not look like the super
     * admin revoking the feature, and neither may overwrite the other.
     */
    @Column(name = "sending_enabled", nullable = false)
    private boolean sendingEnabled = true;

    public WhatsappConfig(UUID adminId, boolean enabled) {
        this.adminId = adminId;
        this.enabled = enabled;
    }
}
