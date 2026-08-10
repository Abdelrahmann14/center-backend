package com.center.student.dto;
import com.center.registration.validation.RegistrationValidation;

import java.util.UUID;

import com.center.common.enums.Religion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * A brand-new student creating their own account (registration Option 2). Field
 * formats (4-part Arabic name, 11-digit phones, strong password) are enforced by
 * {@link com.center.registration.validation.RegistrationValidation} in the service.
 */
public record StudentRegistrationRequest(
        /** Display name - the student's full 4-part Arabic name. */
        @NotBlank(message = "مطلوب") String fullName,
        /** Login name, WITHOUT the domain; the server appends @center.student.com. */
        @NotBlank(message = "مطلوب") String username,
        /** The chosen teacher (Admin) - the workspace the student joins. */
        @NotNull(message = "مطلوب") UUID adminId,
        @NotBlank(message = "مطلوب") String grade,
        /** Date of birth, ISO "yyyy-MM-dd". */
        @NotNull(message = "مطلوب") @Past(message = "تاريخ الميلاد غير صحيح")
        java.time.LocalDate birthDate,
        @NotNull(message = "مطلوب") UUID groupId,
        @NotNull(message = "مطلوب") Religion religion,
        /** Arabic-only, 5 to 25 characters. */
        @NotBlank(message = "مطلوب")
        @Size(min = 5, max = 25, message = "اسم المدرسة يجب أن يكون من 5 إلى 25 حرفًا")
        String school,
        @NotBlank(message = "مطلوب") String city,
        @NotBlank(message = "مطلوب") String studentPhone,
        @NotBlank(message = "مطلوب") String parentPhone,
        @NotBlank(message = "مطلوب") String password,
        @NotBlank(message = "مطلوب") String confirmPassword) {
}
