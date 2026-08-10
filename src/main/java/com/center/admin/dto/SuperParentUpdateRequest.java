package com.center.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Super admin editing a parent's core fields (cross-tenant). */
public record SuperParentUpdateRequest(
        @NotBlank(message = "مطلوب") @Size(max = 120) String name,
        @NotBlank(message = "مطلوب") @Size(max = 20) String phone) {
}
