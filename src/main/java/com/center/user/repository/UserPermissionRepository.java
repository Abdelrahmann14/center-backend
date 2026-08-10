package com.center.user.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.user.entity.UserPermission;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {

    List<UserPermission> findByUserId(UUID userId);

    /** Replace-set support: clear a user's grants before inserting the new list. */
    void deleteByUserId(UUID userId);

    /** One granted permission, named in Arabic, for the assistants table. */
    interface GrantedNameRow {
        UUID getUserId();

        String getNameAr();
    }

    /**
     * Every grant in one workspace, so the assistants list can show what each
     * assistant may do without a query per row.
     */
    @Query(value = """
            SELECT up.user_id AS userId, p.name_ar AS nameAr
            FROM user_permissions up
            JOIN permissions p ON p.id = up.permission_id
            JOIN modules m ON m.id = p.module_id
            WHERE up.admin_id = :adminId
            ORDER BY m.sort_order, p.sort_order
            """, nativeQuery = true)
    List<GrantedNameRow> findGrantedNames(@Param("adminId") UUID adminId);
}
