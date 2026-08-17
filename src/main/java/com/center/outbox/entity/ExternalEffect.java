package com.center.outbox.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

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
 * One piece of work that can only be done while the internet is up: writing a
 * contact to Google, sending a WhatsApp message.
 *
 * <p>Deliberately NOT a {@code @TenantId} entity. The drainer runs on a timer
 * with no request and therefore no tenant bound, and it has to see every
 * workspace's queue at once; the workspace it belongs to is carried in
 * {@link #adminId} and re-bound explicitly before the effect runs.
 */
@Entity
@Table(name = "external_effect_outbox",
        indexes = @Index(name = "external_effect_outbox_due_idx", columnList = "next_attempt_at"))
@Getter
@Setter
@NoArgsConstructor
public class ExternalEffect {

    /** A student's contact card must reach Google Contacts. Ref = student id. */
    public static final String GOOGLE_CONTACT = "GOOGLE_CONTACT";

    /** A lesson's attendance/absence WhatsApp batch. Ref = lecture id. */
    public static final String WHATSAPP_LECTURE = "WHATSAPP_LECTURE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(nullable = false, updatable = false)
    private String kind;

    /** The subject the effect is about; the dedupe key together with kind. */
    @Column(name = "ref_id")
    private UUID refId;

    /** JSON with whatever else the effect needs. Null when ref_id says it all. */
    @Column(columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt = OffsetDateTime.now();

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
