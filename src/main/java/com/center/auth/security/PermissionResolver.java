package com.center.auth.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.center.admin.service.ModuleAccessService;

import lombok.RequiredArgsConstructor;

/**
 * Builds the full authority set for an authenticated principal: their single
 * {@code ROLE_*} plus one {@code PERM_<code>} per effective permission. This is
 * the bridge between the RBAC model and Spring's {@code hasAuthority(...)} checks.
 * Results are cached briefly per user (see {@link PermissionCache}).
 */
@Component
@RequiredArgsConstructor
public class PermissionResolver {

    /** Authority prefix for fine-grained permissions, mirroring {@code ROLE_}. */
    public static final String PERMISSION_PREFIX = "PERM_";

    private final ModuleAccessService moduleAccessService;
    private final PermissionCache cache;

    public List<GrantedAuthority> authorities(AuthenticatedUser user) {
        return cache.get(user.getId(), user.getAdminId(), () -> resolve(user));
    }

    private List<GrantedAuthority> resolve(AuthenticatedUser user) {
        List<GrantedAuthority> out = new ArrayList<>();
        out.add(new SimpleGrantedAuthority(user.getRole().authority()));
        for (String code : moduleAccessService.permissionCodes(
                user.getRole(), user.getAdminId(), user.getId())) {
            out.add(new SimpleGrantedAuthority(PERMISSION_PREFIX + code));
        }
        return out;
    }
}
