package com.center.google.entity;

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

/**
 * One Google account an admin has connected for Contacts sync. Contacts are
 * written to every connected account. Tokens are secrets held only here.
 */
@Entity
@Table(name = "google_account")
@Getter
@Setter
@NoArgsConstructor
public class GoogleAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "access_expiry")
    private OffsetDateTime accessExpiry;
}
