package com.center.admin.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.admin.entity.Module;

public interface ModuleRepository extends JpaRepository<Module, UUID> {

    Optional<Module> findByCode(String code);

    List<Module> findAllByOrderBySortOrder();

    List<Module> findByActiveTrueOrderBySortOrder();

    /**
     * Codes of the modules enabled for one admin (see the enablement algorithm):
     * active, and either not platform-controlled or explicitly on / defaulting on.
     * Native and admin_id-explicit - these tables are intentionally not tenant-scoped.
     */
    @Query(value = """
            SELECT m.code
            FROM modules m
            LEFT JOIN admin_modules am ON am.module_id = m.id AND am.admin_id = :adminId
            WHERE m.is_active
              AND (m.platform_controlled = false OR COALESCE(am.enabled, m.default_enabled))
            ORDER BY m.sort_order
            """, nativeQuery = true)
    List<String> findEnabledModuleCodesForAdmin(@Param("adminId") UUID adminId);

    @Query(value = "SELECT m.code FROM modules m WHERE m.is_active ORDER BY m.sort_order",
            nativeQuery = true)
    List<String> findAllActiveModuleCodes();
}
