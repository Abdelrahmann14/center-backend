package com.center.google.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.google.entity.GoogleAccount;

public interface GoogleAccountRepository extends JpaRepository<GoogleAccount, UUID> {

    List<GoogleAccount> findByAdminIdOrderByEmailAsc(UUID adminId);

    Optional<GoogleAccount> findByAdminIdAndEmail(UUID adminId, String email);
}
