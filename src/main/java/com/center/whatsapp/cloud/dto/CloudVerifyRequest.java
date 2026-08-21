package com.center.whatsapp.cloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The code Meta sent to the number, read back by whoever holds the phone.
 *
 * <p>This is the ONE step the teacher takes: they receive the code and pass it
 * on. Everything else is done for them from the super admin's screen.
 */
public record CloudVerifyRequest(
        @NotBlank(message = "مطلوب")
        @Pattern(regexp = "\\d{6}", message = "الكود ٦ أرقام") String code) {
}
