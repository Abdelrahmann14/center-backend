package com.center.auth.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.center.user.entity.User;
import com.center.common.enums.Role;

import lombok.Getter;

/** The authenticated principal. Carries the user's id so services can audit. */
@Getter
public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String username;
    private final String password;
    private final Role role;

    /**
     * The workspace this principal acts within. For an admin it is their own id
     * (they are the root of their workspace); for an assistant/student it is the
     * owning admin. NULL for a super admin, who owns no workspace.
     */
    private final UUID adminId;

    public AuthenticatedUser(UUID id, String username, String password, Role role, UUID adminId) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.adminId = adminId;
    }

    public static AuthenticatedUser from(User user) {
        UUID tenant = user.getRole() == Role.ADMIN ? user.getId() : user.getAdminId();
        return new AuthenticatedUser(
                user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(), tenant);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
}
