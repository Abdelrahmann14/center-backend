package com.center.admin.service;

import java.util.Set;
import java.util.UUID;

import com.center.common.enums.Role;

/**
 * The single brain of the RBAC model: it decides, from the role + workspace +
 * user, which permission and module codes are effective. Used both to build
 * authorities at auth time and to answer "what can I see" for the frontend, so
 * enforcement and UI can never drift apart.
 */
public interface ModuleAccessService {

    /**
     * Effective permission codes (bare, e.g. {@code EXAM_CREATE} - no PERM_ prefix).
     * SUPER_ADMIN: all. ADMIN: every permission of their enabled modules.
     * USER: their granted permissions intersected with admin-managed, enabled
     * modules. STUDENT/PARENT: none.
     */
    Set<String> permissionCodes(Role role, UUID adminId, UUID userId);

    /** Codes of the modules enabled for the principal's workspace. */
    Set<String> enabledModuleCodes(Role role, UUID adminId);
}
