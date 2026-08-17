package com.center.messaging.entity;

import java.util.UUID;

import com.center.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One wording of an automated message. {@code sortOrder == 0} is the base message
 * the admin wrote; higher orders are the AI-generated alternatives. All are
 * editable, and one is chosen at random per recipient at send time.
 */
@Entity
@Table(name = "wa_message_variant")
@Getter
@Setter
@NoArgsConstructor
public class MessageVariant extends TenantEntity {

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** 0 = the admin-written base; 1.. = AI alternatives. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** When true this wording is rendered to a white image and sent as a picture. */
    @Column(name = "send_as_image", nullable = false)
    private boolean sendAsImage;
}
