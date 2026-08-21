package com.center.messaging.entity;

import com.center.common.entity.TenantEntity;
import com.center.common.enums.AutomationType;
import com.center.common.enums.MessageAudience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One workspace's configuration for an automated WhatsApp message type. The
 * message text itself lives in {@link MessageVariant} rows (a base plus AI
 * alternatives); this row holds the switch, the audience, and - for ABSENCE - the
 * week window that decides when it fires.
 */
@Entity
@Table(name = "wa_message_automation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"admin_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
public class MessageAutomation extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutomationType type;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageAudience audience = MessageAudience.STUDENT;

    /** 0 = Saturday .. 6 = Friday. The day the counted week begins. ABSENCE only. */
    @Column(name = "week_start_day")
    private Short weekStartDay;

    /** 0 = Saturday .. 6 = Friday. The absence message is sent at 23:59 on it. ABSENCE only. */
    @Column(name = "week_end_day")
    private Short weekEndDay;
}
