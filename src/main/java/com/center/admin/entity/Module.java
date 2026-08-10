package com.center.admin.entity;
import com.center.common.entity.BaseEntity;
import com.center.admin.service.ModuleAccessService;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A platform feature in the RBAC catalog. Global (not tenant-scoped): the same
 * catalog is shared by every workspace. The ownership flags encode who controls
 * the module and how it defaults - see {@link com.center.admin.service.ModuleAccessService}.
 */
@Entity
@Table(name = "modules")
@Getter
@Setter
@NoArgsConstructor
public class Module extends BaseEntity {

    /** Stable machine code, e.g. {@code EXAMS}. The catalog's identity. */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "description_ar")
    private String descriptionAr;

    @Column(nullable = false)
    private String category = "general";

    /** The super admin gates this module on/off per admin. */
    @Column(name = "platform_controlled", nullable = false)
    private boolean platformControlled = false;

    /** The admin may assign this module's permissions to their users. */
    @Column(name = "admin_managed", nullable = false)
    private boolean adminManaged = true;

    /**
     * When platform-controlled and an admin has no explicit row, this decides
     * whether the module is on. {@code platform_controlled && !default_enabled}
     * means "disabled until explicitly enabled".
     */
    @Column(name = "default_enabled", nullable = false)
    private boolean defaultEnabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
