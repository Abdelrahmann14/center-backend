package com.center.notification.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A logged super-admin broadcast, for the History panel. */
@Entity
@Table(name = "outgoing_messages")
@Getter
@Setter
@NoArgsConstructor
public class OutgoingMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owning workspace; null = a global/super-admin broadcast. */
    @Column(name = "admin_id", updatable = false)
    private UUID adminId;

    /** 'notification' or 'whatsapp' (whatsapp = also sent over WhatsApp). */
    @Column(nullable = false, updatable = false)
    private String channel;

    @Column(nullable = false, updatable = false)
    private String sender;

    @Column(updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private String body;

    /** Human summary of the selected recipient categories. */
    @Column(updatable = false)
    private String audience;

    @Column(nullable = false, updatable = false)
    private int recipients;

    // Updatable: the broadcast row is saved once up-front (to mint its id for
    // notification tagging), then re-saved after the send loop with the final tally.
    @Column(name = "whatsapp_sent", nullable = false)
    private int whatsappSent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
