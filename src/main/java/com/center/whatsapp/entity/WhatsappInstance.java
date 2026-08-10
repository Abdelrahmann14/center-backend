package com.center.whatsapp.entity;
import com.center.common.entity.BaseEntity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Green API WhatsApp instance linked from inside the app. {@code ownerAdminId}
 * is null for the single super-admin / platform instance; a non-null owner scopes
 * an instance to one teacher (future use).
 *
 * <p>The instance id + api token come from the Green API console once; the actual
 * phone is linked by scanning the QR code shown in the Services page, so no visit
 * to the console is needed to connect a number.
 */
@Entity
@Table(name = "whatsapp_instance")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappInstance extends BaseEntity {

    /** Null = a super-admin / platform instance. */
    @Column(name = "owner_admin_id")
    private UUID ownerAdminId;

    /** Friendly name shown in the Services UI (e.g. "الرقم الأساسي"). */
    @Column(name = "label")
    private String label;

    @Column(name = "instance_id", nullable = false)
    private String instanceId;

    @Column(name = "api_token", nullable = false)
    private String apiToken;

    @Column(name = "base_url", nullable = false)
    private String baseUrl = "https://api.green-api.com";

    /** Cached from the last state check: the linked phone, once authorized. */
    @Column(name = "phone")
    private String phone;

    /** Cached Green API state: authorized | notAuthorized | starting | ... */
    @Column(name = "state")
    private String state;
}
