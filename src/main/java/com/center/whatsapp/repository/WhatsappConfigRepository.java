package com.center.whatsapp.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.whatsapp.entity.WhatsappConfig;

public interface WhatsappConfigRepository extends JpaRepository<WhatsappConfig, UUID> {
}
