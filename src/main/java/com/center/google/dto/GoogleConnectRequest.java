package com.center.google.dto;

import jakarta.validation.constraints.NotBlank;

/** The OAuth authorization code returned to the app after Google consent. */
public record GoogleConnectRequest(@NotBlank(message = "مطلوب") String code) {
}
