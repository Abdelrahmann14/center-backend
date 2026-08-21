package com.center.whatsapp.cloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The two-step verification PIN that finishes registering a number.
 *
 * <p>Meta asks for this PIN again on any future re-registration of the same
 * number, and will not reveal or reset it without a support ticket. It is NOT
 * stored by this system: whoever provisions the number must keep it. Sending it
 * and forgetting it is the deliberate choice - a PIN kept in the same database as
 * everything else is one breach away from letting someone re-register the number
 * elsewhere.
 */
public record CloudRegisterRequest(
        @NotBlank(message = "مطلوب")
        @Pattern(regexp = "\\d{6}", message = "الرقم السري ٦ أرقام") String pin) {
}
