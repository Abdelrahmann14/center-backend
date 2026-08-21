package com.center.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.user.entity.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    List<Permission> findByModuleIdOrderBySortOrder(UUID moduleId);

    List<Permission> findByCodeIn(Collection<String> codes);

    /** Every permission of every active module - the super admin's effective set. */
    @Query(value = """
            SELECT p.code
            FROM permissions p
            JOIN modules m ON m.id = p.module_id
            WHERE m.is_active
            """, nativeQuery = true)
    List<String> findAllActivePermissionCodes();

    /** Every permission whose module is enabled for the admin - an admin's implicit set. */
    @Query(value = """
            SELECT p.code
            FROM permissions p
            JOIN modules m ON m.id = p.module_id
            LEFT JOIN admin_modules am ON am.module_id = m.id AND am.admin_id = :adminId
            WHERE m.is_active
              AND (m.platform_controlled = false OR COALESCE(am.enabled, m.default_enabled))
            """, nativeQuery = true)
    List<String> findAdminPermissionCodes(@Param("adminId") UUID adminId);

    /**
     * The permissions an admin may assign: those of modules that are admin-managed
     * AND currently enabled for them. Used to validate grants and to build the
     * grouped-checkbox catalog.
     */
    @Query(value = """
            SELECT p.code
            FROM permissions p
            JOIN modules m ON m.id = p.module_id
            LEFT JOIN admin_modules am ON am.module_id = m.id AND am.admin_id = :adminId
            WHERE m.is_active
              AND m.admin_managed = true
              AND (m.platform_controlled = false OR COALESCE(am.enabled, m.default_enabled))
            """, nativeQuery = true)
    List<String> findAdminManagedPermissionCodes(@Param("adminId") UUID adminId);

    /**
     * A user's granted permissions, filtered to modules that are admin-managed AND
     * currently enabled for their admin - so a disabled module makes its grants
     * inert without deleting them.
     */
    @Query(value = """
            SELECT p.code
            FROM user_permissions up
            JOIN permissions p ON p.id = up.permission_id
            JOIN modules m ON m.id = p.module_id
            LEFT JOIN admin_modules am ON am.module_id = m.id AND am.admin_id = :adminId
            WHERE up.user_id = :userId
              AND m.is_active
              AND m.admin_managed = true
              AND (m.platform_controlled = false OR COALESCE(am.enabled, m.default_enabled))
            """, nativeQuery = true)
    List<String> findUserPermissionCodes(@Param("userId") UUID userId, @Param("adminId") UUID adminId);
}
