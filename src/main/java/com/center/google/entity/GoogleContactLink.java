package com.center.google.entity;

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
 * Maps a synced subject (student/parent/both) phone to its Google contact in one
 * connected account, so re-syncs update the same contact and never duplicate it.
 */
@Entity
@Table(name = "google_contact_link")
@Getter
@Setter
@NoArgsConstructor
public class GoogleContactLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "google_account_id", nullable = false)
    private UUID googleAccountId;

    /** 'student' | 'parent' | 'both'. */
    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "resource_name", nullable = false)
    private String resourceName;

    @Column(name = "etag")
    private String etag;
}
