package com.center.admin.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.admin.entity.AdminModule;

public interface AdminModuleRepository extends JpaRepository<AdminModule, UUID> {

    List<AdminModule> findByAdminId(UUID adminId);

    Optional<AdminModule> findByAdminIdAndModuleId(UUID adminId, UUID moduleId);
}
