package com.center.center.entity;
import com.center.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "centers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"admin_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Center extends TenantEntity {

    // Unique per Admin, not globally - two teachers may each have a "Main".
    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
