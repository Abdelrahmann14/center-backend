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
 * One WhatsApp number the app can send through, on the platform's official
 * WhatsApp Business Account. {@code ownerAdminId} is null for the platform's own
 * number; a non-null owner scopes a number to one teacher.
 *
 * <p>There is no credential on the row. Meta authenticates the BUSINESS, not the
 * number: one token in the environment covers every number, and {@code
 * phoneNumberId} is the address a send is aimed at.
 *
 * <p>{@code state} is where provisioning got to - {@code pending} (added, not yet
 * verified), {@code verified} (code confirmed), {@code authorized} (registered and
 * able to send). Only an authorized number is ever resolved for a send.
 */
@Entity
@Table(name = "whatsapp_instance")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappInstance extends BaseEntity {

    /** Null = the platform's own number rather than a teacher's. */
    @Column(name = "owner_admin_id")
    private UUID ownerAdminId;

    /** Friendly name shown in the UI (e.g. "الرقم الأساسي"). */
    @Column(name = "label")
    private String label;

    /** Meta's id for the number - the address every send goes to. */
    @Column(name = "phone_number_id", nullable = false)
    private String phoneNumberId;

    /** The WhatsApp Business Account the number was added under. */
    @Column(name = "waba_id")
    private String wabaId;

    /** The name recipients see, once Meta has approved it. */
    @Column(name = "display_name")
    private String displayName;

    /** GREEN | YELLOW | RED, as Meta last reported it. */
    @Column(name = "quality_rating")
    private String qualityRating;

    /** The line itself, as Meta formats it. */
    @Column(name = "phone")
    private String phone;

    /** pending | verified | authorized. */
    @Column(name = "state")
    private String state;
}
