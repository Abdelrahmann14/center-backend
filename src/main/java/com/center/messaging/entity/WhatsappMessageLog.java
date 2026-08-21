package com.center.messaging.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.TenantId;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One WhatsApp message the system attempted to send, to one recipient. Append-only
 * (like {@code attendance}); it is the source for the Messages page history table,
 * so it records who it went to, what was sent, and whether it went through.
 */
@Entity
@Table(name = "wa_message_log",
        indexes = @Index(name = "wa_message_log_admin_created_idx", columnList = "admin_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class WhatsappMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(name = "recipient_name")
    private String recipientName;

    private String phone;

    /** The student's serial (student/recipient code), as text. */
    @Column(name = "recipient_code")
    private String recipientCode;

    /** STUDENT | PARENT - whose number this went to. */
    @Column(name = "recipient_type", nullable = false)
    private String recipientType;

    @Column(name = "student_id")
    private UUID studentId;

    /** The lesson this message was about (attendance/absence); null for a broadcast. */
    @Column(name = "lecture_id")
    private UUID lectureId;

    /** The group this message was about (attendance/absence); null for a broadcast. */
    @Column(name = "group_id")
    private UUID groupId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** SENT | FAILED. */
    @Column(nullable = false)
    private String status;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    /**
     * Meta's numeric error code when the send failed; null when it went.
     *
     * <p>Kept beside the sentence because only the code is stable enough to
     * branch on - Meta rewords the text freely. 131026 ("message undeliverable")
     * is the one that carries information about the RECIPIENT rather than about
     * us, and it is what the WhatsApp-reachability answer is derived from.
     */
    @Column(name = "failure_code")
    private Integer failureCode;

    /** SYSTEM | MANUAL. */
    @Column(nullable = false)
    private String source;

    /** ATTENDANCE | ABSENCE | MANUAL. */
    @Column(nullable = false)
    private String origin;

    /**
     * The number this left from, so usage can be reported per number. Null when
     * nothing was able to send, which is to say the attempt failed before a
     * number was chosen.
     */
    @Column(name = "instance_id")
    private UUID instanceId;

    /**
     * The template that carried it, and its billing category. Copied rather than
     * joined: a template can be renamed or deleted in WhatsApp Manager, and last
     * month's cost report must not move when it is.
     */
    @Column(name = "template_name")
    private String templateName;

    @Column(name = "template_category")
    private String templateCategory;

    /**
     * WhatsApp's own message id. It is the only handle the delivery webhook gives
     * us, so it is what {@code delivered_at}/{@code read_at} are matched on later.
     * Null on an attempt that never reached WhatsApp.
     */
    @Column(name = "wamid")
    private String wamid;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "sent_by_user_id")
    private UUID sentByUserId;

    @Column(name = "sent_by_name")
    private String sentByName;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
