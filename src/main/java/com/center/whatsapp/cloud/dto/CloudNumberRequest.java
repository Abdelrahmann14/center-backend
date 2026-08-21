package com.center.whatsapp.cloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Adds one teacher's number to the platform's WhatsApp Business Account.
 *
 * <p>The country code is separate from the number because Meta wants them apart,
 * and {@code displayName} is what recipients see in their chat list - Meta
 * reviews it, so it must read like the business it represents.
 *
 * @param countryCode without a plus, e.g. {@code 20}
 * @param localNumber without the leading zero, e.g. {@code 1038630485}
 * @param displayName the name Meta reviews and recipients see
 * @param label       the internal name for the number, shown in the app only
 */
public record CloudNumberRequest(
        @NotBlank(message = "مطلوب") @Pattern(regexp = "\\d{1,4}", message = "كود الدولة أرقام فقط")
        String countryCode,

        @NotBlank(message = "مطلوب") @Pattern(regexp = "\\d{6,15}", message = "رقم غير صحيح")
        String localNumber,

        @NotBlank(message = "مطلوب") @Size(max = 120) String displayName,

        @Size(max = 60) String label) {
}
