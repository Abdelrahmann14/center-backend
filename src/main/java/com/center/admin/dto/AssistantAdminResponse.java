package com.center.admin.dto;

import java.util.UUID;

/** One assistant inside a teacher's detail page (super admin view). */
public record AssistantAdminResponse(
        UUID id,
        String username,
        String email,
        boolean active,
        String photo) {
}
