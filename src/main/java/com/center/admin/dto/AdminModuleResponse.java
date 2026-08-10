package com.center.admin.dto;

/**
 * One platform module in a super admin's per-admin toggle view, with its resolved
 * enabled state for that admin.
 */
public record AdminModuleResponse(
        String code,
        String nameAr,
        String descriptionAr,
        String category,
        boolean enabled) {
}
