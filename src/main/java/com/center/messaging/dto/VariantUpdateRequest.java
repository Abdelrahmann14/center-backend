package com.center.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Edits one alternative wording of an automated message and its image flag. */
public record VariantUpdateRequest(
        @NotBlank(message = "مطلوب") @Size(max = 2000) String body,
        Boolean sendAsImage) {
}
