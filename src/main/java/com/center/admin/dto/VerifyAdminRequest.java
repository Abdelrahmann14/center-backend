package com.center.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyAdminRequest(@NotBlank(message = "مطلوب") String password) {
}
