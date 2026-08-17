package com.center.finance.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.center.finance.dto.AssistantAttendanceRecordResponse;
import com.center.finance.dto.AssistantAttendanceResponse;
import com.center.finance.dto.AttendanceRequest;
import com.center.finance.dto.FinanceEntryRequest;
import com.center.finance.dto.FinanceEntryResponse;
import com.center.finance.dto.InvoiceResponse;

public interface FinanceService {

    /** Every lesson session held in the window, newest day first. */
    List<InvoiceResponse> invoices(LocalDate from, LocalDate to);

    /** One session's invoice, or a not-found when nobody registered for it. */
    InvoiceResponse invoice(UUID lectureId, UUID groupId, LocalDate sessionDate);

    /** The admin's assistants, each flagged with whether they attended this session. */
    List<AssistantAttendanceResponse> sessionAttendance(UUID lectureId, UUID groupId, LocalDate sessionDate);

    /** Replaces a session's attendance with exactly the assistants in the request. */
    void setAttendance(AttendanceRequest request);

    /** One assistant's attended sessions, newest first, for the Assistants page. */
    List<AssistantAttendanceRecordResponse> assistantAttendanceLog(UUID userId);

    FinanceEntryResponse addEntry(FinanceEntryRequest request);

    FinanceEntryResponse updateEntry(UUID entryId, FinanceEntryRequest request);

    /** The offline replay path: same end state whichever delivery arrives first. */
    FinanceEntryResponse upsertEntry(UUID entryId, FinanceEntryRequest request);

    void deleteEntry(UUID entryId);
}
