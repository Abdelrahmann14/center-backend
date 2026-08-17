package com.center.whatsapp.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Whether one phone number is reachable on WhatsApp.
 *
 * <p>The answer does not change, so it is worth remembering: without this every
 * student form and every re-edit spent another Green API round trip on a number
 * already asked about. It is also what makes the check work offline - a number
 * typed with no connection is stored unanswered and resolved later by the
 * background job.
 *
 * <p>Deliberately not a {@code BaseEntity}: nobody edits these rows by hand, so
 * auditing columns and an optimistic-lock version would be dead weight, the same
 * reasoning the other machine-written tables follow. {@link TenantId} still
 * scopes it, because the answer comes from that workspace's own Green API
 * instance.
 */
@Entity
@Table(name = "whatsapp_numbers")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    /** Digits only, as the client normalises it before asking. */
    @Column(nullable = false, updatable = false)
    private String phone;

    /**
     * {@code null} means queued and never successfully answered - which is not
     * the same as {@code false}, "asked, and this number is not on WhatsApp".
     * Only a real Green API answer ever writes this column.
     */
    @Column(name = "has_whatsapp")
    private Boolean hasWhatsapp;

    @Column(name = "checked_at")
    private OffsetDateTime checkedAt;

    /** Why the last attempt failed, for the retry job. Cleared on success. */
    @Column(name = "last_error")
    private String lastError;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public WhatsappNumber(String phone) {
        this.phone = phone;
    }

    /** True once a real answer has been recorded. */
    public boolean answered() {
        return hasWhatsapp != null;
    }
}
