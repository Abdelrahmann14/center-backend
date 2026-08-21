package com.center.grade.entity;
import com.center.common.entity.BaseEntity;

import com.center.common.enums.TrackKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single global academic grade (الصف). Managed only by the super admin; every
 * Admin reads the same list. Not tenant-scoped - names are globally unique.
 */
@Entity
@Table(name = "grades")
@Getter
@Setter
@NoArgsConstructor
public class Grade extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Drives which الشعبة options the student form offers. */
    @Column(name = "track_kind", nullable = false)
    private TrackKind trackKind = TrackKind.NONE;

    /**
     * Where this grade sits in the school year sequence, low first.
     *
     * <p>Every select in the app reads the same list, so ordering it here is what
     * makes الصف appear in school order everywhere at once. A new grade defaults
     * to the end rather than the middle - guessing its place from its name would
     * be wrong exactly when the name is unusual.
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}
