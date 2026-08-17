package com.center.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.center.common.enums.FinanceEntryKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A manual invoice line. The session it belongs to travels with it: the line is
 * written from a specific invoice, and only that invoice should show it.
 */
public record FinanceEntryRequest(
        @NotNull(message = "مطلوب") UUID lectureId,
        UUID groupId,
        @NotNull(message = "مطلوب") LocalDate sessionDate,
        @NotNull(message = "مطلوب") FinanceEntryKind kind,
        @NotBlank(message = "مطلوب") @Size(max = 200, message = "البيان طويل") String description,
        @NotNull(message = "مطلوب") @PositiveOrZero(message = "المبلغ لا يقل عن صفر") BigDecimal amount) {
}
