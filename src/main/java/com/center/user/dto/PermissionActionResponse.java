package com.center.user.dto;

import java.util.UUID;

/** One assignable permission (a checkbox) within a module group. */
public record PermissionActionResponse(UUID id, String code, String action, String nameAr) {
}
