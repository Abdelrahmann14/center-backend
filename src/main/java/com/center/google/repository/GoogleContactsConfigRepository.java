package com.center.google.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.google.entity.GoogleContactsConfig;

public interface GoogleContactsConfigRepository extends JpaRepository<GoogleContactsConfig, UUID> {
}
