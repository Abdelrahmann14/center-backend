package com.center.parent.entity;
import com.center.common.entity.BaseEntity;
import com.center.common.entity.TenantEntity;
import com.center.user.entity.User;

import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A guardian's profile, paired with a {@code users} row of role {@code parent}.
 *
 * <p>Not a {@link TenantEntity}: a parent may link to students across several
 * workspaces, so - like {@link User} - it owns none. It is scoped by the explicit
 * links in {@code parent_student_links} instead of a tenant discriminator.
 */
@Entity
@Table(name = "parents", indexes = {
        @Index(name = "parents_user_id_idx", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Parent extends BaseEntity {

    /** The Parent Code - a global sequence number, shown on the account page. */
    @Generated(event = EventType.INSERT)
    @Column(name = "serial", insertable = false, updatable = false, unique = true)
    private Integer serial;

    /** Display name - the parent's full 3-part Arabic name. */
    @Column(nullable = false)
    private String name;

    /**
     * The trusted parent phone. On approval it is copied onto each linked
     * student, and changing it here re-syncs every linked student.
     */
    @Column(nullable = false)
    private String phone;

    /** The login account (role {@code parent}). */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
}
