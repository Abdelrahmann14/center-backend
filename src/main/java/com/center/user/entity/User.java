package com.center.user.entity;
import com.center.common.entity.BaseEntity;
import com.center.common.validation.EmailPolicy;

import java.math.BigDecimal;
import java.util.UUID;

import com.center.common.enums.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /**
     * The human DISPLAY NAME shown throughout the app (profile, student info).
     * Since V21 it no longer authenticates anything - {@link #email} does.
     */
    @Column(nullable = false)
    private String username;

    /**
     * The login identifier: {@code <localPart>@center.<role>.com}, built server-side
     * from the typed local part and the account's role (see
     * {@link com.center.common.validation.EmailPolicy}). Globally unique, matched
     * case-insensitively - enforced by a unique index on {@code lower(email)}.
     */
    @Column(nullable = false)
    private String email;

    /**
     * The Admin whose workspace this user belongs to. NULL for an admin (it is
     * the root of its own workspace) and for the super admin. Assistants and
     * students always point at their owning admin.
     *
     * <p>Not a {@code @TenantId}: login and user administration must query across
     * this boundary, so users are scoped by explicit admin_id filters instead.
     */
    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Role role = Role.USER;

    /** Contact number, digits only. Optional and not used for login. */
    private String phone;

    /**
     * A deactivated account is refused at login but its data is kept. When an
     * Admin is inactive, its whole workspace is locked out (assistants are
     * refused because their owning Admin is inactive).
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Profile photo bytes (bytea), uploaded by the super admin. Null = none. */
    @Column(name = "photo_data")
    private byte[] photoData;

    /** MIME type of {@link #photoData}, e.g. {@code image/png}. */
    @Column(name = "photo_type")
    private String photoType;
}
