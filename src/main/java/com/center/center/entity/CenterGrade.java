package com.center.center.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A center's price for one grade. Child row of a center, fully replaced on
 * every center save, so it carries no auditing of its own.
 */
@Entity
@Table(name = "center_grades")
@Getter
@Setter
@NoArgsConstructor
public class CenterGrade {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "center_id", nullable = false)
        private UUID centerId;

        @Column(nullable = false)
        private String grade;
    }

    @EmbeddedId
    private Key id;

    /** Owning Admin. Filled on insert and filtered on read by Hibernate. */
    @TenantId
    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    public CenterGrade(UUID centerId, String grade, BigDecimal price) {
        this.id = new Key(centerId, grade);
        this.price = price;
    }
}
