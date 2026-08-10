package com.center.user.entity;
import com.center.common.entity.BaseEntity;
import com.center.admin.entity.Module;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A fine-grained action within a {@link Module}, e.g. {@code EXAM_CREATE}. Global
 * catalog row. The {@code moduleId} is a plain column (not an association) so the
 * resolver can read permissions during authentication without an open session.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission extends BaseEntity {

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    /** Stable machine code, e.g. {@code EXAM_CREATE}. Becomes {@code PERM_<code>}. */
    @Column(nullable = false, unique = true)
    private String code;

    /** The verb, e.g. {@code CREATE} - used to group actions in the UI. */
    @Column(nullable = false)
    private String action;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
