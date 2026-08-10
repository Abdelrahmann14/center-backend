package com.center.user.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.center.common.enums.Role;

public record UserResponse(
        UUID id,
        /** Display name. */
        String username,
        /** Login address - {@code <local>@center.assistant.com}. */
        String email,
        /** Contact number; optional. */
        String phone,
        Role role,
        OffsetDateTime createdAt,
        /** Arabic names of the permissions granted to this assistant. */
        List<String> permissions) {
}
