package com.center.whatsapp.check;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One number and whether it is on WhatsApp.
 *
 * <p>The only entity in the system with no {@code admin_id}. Every other row
 * belongs to a teacher's workspace; this one belongs to the number - the answer
 * is the same whoever asks - so it is shared, and a guardian on two teachers'
 * rosters is checked once between them.
 */
@Entity
@Table(name = "wa_number_check")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappNumberCheck {

    /** Local Egyptian form, as the roster stores it. */
    @Id
    private String phone;

    @Column(name = "exists_whatsapp", nullable = false)
    private boolean existsWhatsapp;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt = OffsetDateTime.now();
}
