package com.center.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Changing one's own phone from the account page. For a parent this re-syncs the
 * number onto every linked student; for a student it updates their own number.
 */
public record ChangePhoneRequest(
        @NotBlank(message = "مطلوب") String phone) {
}
