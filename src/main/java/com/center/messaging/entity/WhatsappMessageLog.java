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

    /** SYSTEM | MANUAL. */
    @Column(nullable = false)
    private String source;

    /** ATTENDANCE | ABSENCE | MANUAL. */
    @Column(nullable = false)
    private String origin;

    @Column(name = "sent_by_user_id")
    private UUID sentByUserId;

    @Column(name = "sent_by_name")
    private String sentByName;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
