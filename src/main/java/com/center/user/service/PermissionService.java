package com.center.user.service;

import java.util.List;
import java.util.UUID;

import com.center.user.dto.PermissionModuleResponse;

/** Level-2 control: an admin assigning fine-grained permissions to their users. */
public interface PermissionService {

    /**
     * The grouped, assignable permission catalog for the current admin - only
     * admin-managed modules that are currently enabled for them.
     */
    List<PermissionModuleResponse> catalog();

    /** The permission codes currently granted to one of the admin's users. */
    List<String> userPermissions(UUID userId);

    /** Replace a user's grants with exactly {@code codes} (validated against the catalog). */
    void setUserPermissions(UUID userId, List<String> codes);
}
