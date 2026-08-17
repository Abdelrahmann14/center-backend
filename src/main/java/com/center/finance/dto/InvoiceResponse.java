package com.center.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * One lesson session read as an invoice.
 *
 * <p>Everything above {@code entries} is derived from the registrations and can
 * never be edited by hand; the manual lines are kept apart so the UI and the PDF
 * can both say which is which.
 *
 * @param key           the session's stable identity, {@code lecture:group:date}
 * @param students      how many are enrolled in the group today, not how many attended
 * @param attended      present students on this session
 * @param lessonPrice   the center's official price for this group's grade
 * @param lines         attendance bucketed by what each student pays, dearest first
 * @param gross         the takings before the center's share
 * @param percentage    the center's share of this grade at this center, 0-100
 * @param centerCut     what that share works out to, rounded up
 * @param netAfterCut   gross minus the center's share
 * @param otherIncome   the manual income lines, summed
 * @param otherExpense  the manual expense lines, summed
 * @param total         what the teacher should end up receiving
 * @param attendees     names of the assistants marked present, for the card + PDF
 */
public record InvoiceResponse(
        String key,
        UUID lectureId,
        String lectureName,
        UUID groupId,
        String groupLabel,
        String centerName,
        String grade,
        LocalDate sessionDate,
        LocalTime startTime,
        long students,
        long attended,
        BigDecimal lessonPrice,
        List<InvoiceLineResponse> lines,
        BigDecimal gross,
        BigDecimal percentage,
        BigDecimal centerCut,
        BigDecimal netAfterCut,
        List<FinanceEntryResponse> entries,
        BigDecimal otherIncome,
        BigDecimal otherExpense,
        BigDecimal total,
        List<String> attendees) {
}
