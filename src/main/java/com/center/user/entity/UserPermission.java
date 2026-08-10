package com.center.user.entity;
import com.center.admin.entity.AdminModule;
import com.center.common.entity.BaseEntity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Level-2 grant: one permission held by one user. {@code adminId} is denormalized
 * so the resolver can filter by the owning workspace at auth time (before the
 * tenant is bound) and so an admin's management queries scope cheaply. Not
 * tenant-scoped, for the same reason as {@link AdminModule}.
 */
@Entity
@Table(name = "user_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserPermission extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    /** The admin who granted it; null for the migration backfill. */
    @Column(name = "granted_by")
    private UUID grantedBy;
}
