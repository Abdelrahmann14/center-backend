package com.center.auth.service;
import com.center.admin.service.ModuleAccessService;

import java.util.List;

import org.springframework.stereotype.Component;

import com.center.auth.dto.AuthenticatedUserResponse;
import com.center.user.entity.User;
import com.center.auth.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Builds the signed-in account view enriched with the effective RBAC set. One
 * source of truth for login, auto-login (student/parent signup) and /auth/me, so
 * every entry point returns the same permissions/modules shape.
 */
@Component
@RequiredArgsConstructor
public class PrincipalViewFactory {

    private final ModuleAccessService moduleAccessService;

    public AuthenticatedUserResponse of(User user) {
        // An admin's tenant is their own id, an assistant's/student's is their
        // owning admin - resolved by AuthenticatedUser.from.
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        List<String> permissions = moduleAccessService
                .permissionCodes(principal.getRole(), principal.getAdminId(), principal.getId())
                .stream().sorted().toList();
        List<String> modules = moduleAccessService
                .enabledModuleCodes(principal.getRole(), principal.getAdminId())
                .stream().sorted().toList();
        return new AuthenticatedUserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole(), permissions, modules);
    }
}
