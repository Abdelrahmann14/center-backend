package com.center.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** A profile photo as a base64 data URL ({@code data:image/...;base64,...}). */
public record PhotoUploadRequest(@NotBlank(message = "مطلوب") String data) {
}
