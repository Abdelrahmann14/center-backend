package com.center.parent.dto;
import com.center.common.validation.EmailPolicy;
import com.center.registration.validation.RegistrationValidation;
import com.center.student.entity.Student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A guardian creating their account against an existing student. Formats (3-part
 * Arabic name, 11-digit phone, strong password) are enforced by
 * {@link com.center.registration.validation.RegistrationValidation} in the service; the email
 * local part by {@link com.center.common.validation.EmailPolicy}.
 */
public record ParentRegistrationRequest(
        /** The child's Student Code - the account is created against it. */
        @NotNull(message = "مطلوب") @Positive Integer serial,
        /** Login name, WITHOUT the domain; the server appends @center.parent.com. */
        @NotBlank(message = "مطلوب") String username,
        /** Display name - the parent's full 3-part Arabic name. */
        @NotBlank(message = "مطلوب") String fullName,
        /** WhatsApp number, 11 digits - the trusted parent phone once approved. */
        @NotBlank(message = "مطلوب") String phone,
        @NotBlank(message = "مطلوب") String password,
        @NotBlank(message = "مطلوب") String confirmPassword) {
}
