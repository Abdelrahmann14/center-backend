package com.center.admin.entity;
import com.center.common.entity.BaseEntity;
import com.center.user.entity.User;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Level-1 grant: whether a platform module is enabled for one admin. Written by
 * the super admin while unbound and read during authentication before the tenant
 * is bound, so it is deliberately NOT tenant-scoped - {@code adminId} is a plain
 * column, like {@link User}. An absent row falls back to {@code module.default_enabled}.
 */
@Entity
@Table(name = "admin_modules",
        uniqueConstraints = @UniqueConstraint(columnNames = {"admin_id", "module_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AdminModule extends BaseEntity {

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(nullable = false)
    private boolean enabled = true;
}
