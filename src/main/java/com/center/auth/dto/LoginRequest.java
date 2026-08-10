package com.center.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login credentials. Since V21 every account - super admin, admin, assistant and
 * student - signs in with its email address; the backend resolves role, owning
 * Admin and permissions from the resolved account, never from the address itself.
 */
public record LoginRequest(
        @NotBlank(message = "مطلوب") String email,
        @NotBlank(message = "مطلوب") String password) {
}
