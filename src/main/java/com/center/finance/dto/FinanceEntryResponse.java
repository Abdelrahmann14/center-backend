package com.center.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.center.common.enums.FinanceEntryKind;

/** A manual income or expense line as the invoice shows it. */
public record FinanceEntryResponse(
        UUID id,
        UUID lectureId,
        UUID groupId,
        LocalDate sessionDate,
        FinanceEntryKind kind,
        String description,
        BigDecimal amount,
        long version) {
}
