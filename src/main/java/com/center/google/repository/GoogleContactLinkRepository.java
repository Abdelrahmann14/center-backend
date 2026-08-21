package com.center.google.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.center.google.entity.GoogleContactLink;

public interface GoogleContactLinkRepository extends JpaRepository<GoogleContactLink, UUID> {

    Optional<GoogleContactLink> findByGoogleAccountIdAndPhone(UUID googleAccountId, String phone);

    /** Distinct student ids that have at least one synced Google contact, for one admin. */
    @Query("select distinct l.subjectId from GoogleContactLink l where l.adminId = :adminId")
    List<UUID> findSyncedSubjectIds(UUID adminId);

    List<GoogleContactLink> findByAdminId(UUID adminId);
}
