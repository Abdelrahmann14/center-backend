package com.center.auth.dto;

import java.util.List;
import java.util.UUID;

import com.center.common.enums.Role;

/**
 * The signed-in account. {@code username} is the display name shown in the UI;
 * {@code email} is what the account authenticates with. {@code permissions} are
 * the effective fine-grained permission codes (the web app gates UI on them) and
 * {@code modules} are the modules enabled for this workspace - both resolved
 * server-side under the RBAC model.
 */
public record AuthenticatedUserResponse(
        UUID id,
        String username,
        String email,
        Role role,
        List<String> permissions,
        List<String> modules,
        /** Profile photo as a base64 data URL, or null when none is set. */
        String photo) {
}
