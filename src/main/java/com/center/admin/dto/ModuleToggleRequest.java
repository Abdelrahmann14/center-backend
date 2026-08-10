package com.center.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Super admin toggling one platform module for one admin. */
public record ModuleToggleRequest(@NotNull(message = "مطلوب") Boolean enabled) {
}
