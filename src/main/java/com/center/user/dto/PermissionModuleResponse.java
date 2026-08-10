package com.center.user.dto;

import java.util.List;

/**
 * A module heading with its assignable permissions - the shape the admin's
 * grouped-checkbox editor renders. Only admin-managed modules currently enabled
 * for the admin appear here.
 */
public record PermissionModuleResponse(
        String code,
        String nameAr,
        String descriptionAr,
        String category,
        List<PermissionActionResponse> permissions) {
}
