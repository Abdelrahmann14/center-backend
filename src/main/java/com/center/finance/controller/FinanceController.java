package com.center.finance.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.finance.dto.AssistantAttendanceRecordResponse;
import com.center.finance.dto.AssistantAttendanceResponse;
import com.center.finance.dto.AttendanceRequest;
import com.center.finance.dto.FinanceEntryRequest;
import com.center.finance.dto.FinanceEntryResponse;
import com.center.finance.dto.InvoiceResponse;
import com.center.finance.service.FinanceService;
import com.center.finance.service.InvoiceDocumentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The Financials screen: one invoice per lesson session, plus the manual lines
 * that ride on it.
 *
 * <p>A session is addressed by (lecture, group, date) rather than by an id,
 * because it has none - it is derived from the registrations, so the three
 * columns that define it are its key.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@Tag(name = "Financials")
public class FinanceController {

    private final FinanceService financeService;
    private final InvoiceDocumentService documentService;

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('PERM_FINANCE_VIEW')")
    @Operation(summary = "Lesson invoices for a date window",
            description = "Newest day first. Pass the same date twice for a single day.")
    public List<InvoiceResponse> invoices(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return financeService.invoices(from, to);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_FINANCE_VIEW')")
    @Operation(summary = "Add a manual income or expense line to an invoice")
    public FinanceEntryResponse add(@Valid @RequestBody FinanceEntryRequest request) {
        return financeService.addEntry(request);
    }

    @PutMapping("/entries/{entryId}")
    @PreAuthorize("hasAuthority('PERM_FINANCE_VIEW')")
    @Operation(summary = "Edit a manual line")
    public FinanceEntryResponse update(@PathVariable UUID entryId,
            @Valid @RequestBody FinanceEntryRequest request) {
        return financeService.updateEntry(entryId, request);
    }

    @DeleteMapping("/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_FINANCE_VIEW')")
    @Operation(summary = "Delete a manual line")
    public void delete(@PathVariable UUID entryId) {
        financeService.deleteEntry(entryId);
    }

    @GetMapping("/invoices/pdf")
    @PreAuthorize("hasAuthority('PERM_FINANCE_VIEW')")
    @Operation(summary = "One session's invoice as a PDF")
    public ResponseEntity<byte[]> pdf(
            @RequestParam("lecture_id") UUID lectureId,
            @RequestParam(name = "group_id", required = false) UUID groupId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        byte[] pdf = documentService.renderPdf(lectureId, groupId, date);
        // The file name is Arabic, so it can only travel in the RFC 5987 form.
        String encoded = URLEncoder.encode(documentService.fileName(lectureId, groupId, date),
                StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(pdf);
    }

    @PostMapping("/invoices/send")
    @PreAuthorize("hasAuthority('PERM_FINANCE_VIEW')")
    @Operation(summary = "Send the invoice PDF to the teacher's WhatsApp number")
    public Map<String, String> send(
            @RequestParam("lecture_id") UUID lectureId,
            @RequestParam(name = "group_id", required = false) UUID groupId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Map.of("phone", documentService.sendToAdmin(lectureId, groupId, date));
    }

    @GetMapping("/invoices/attendance")
    @PreAuthorize("hasAuthority('PERM_ASSISTANT_ATTENDANCE')")
    @Operation(summary = "The admin's assistants, each flagged as present or not for this session")
    public List<AssistantAttendanceResponse> attendance(
            @RequestParam("lecture_id") UUID lectureId,
            @RequestParam(name = "group_id", required = false) UUID groupId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return financeService.sessionAttendance(lectureId, groupId, date);
    }

    @PutMapping("/invoices/attendance")
    @PreAuthorize("hasAuthority('PERM_ASSISTANT_ATTENDANCE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set which assistants attended a session (replaces the set)")
    public void setAttendance(@Valid @RequestBody AttendanceRequest request) {
        financeService.setAttendance(request);
    }

    @GetMapping("/assistants/{userId}/attendance")
    @PreAuthorize("hasAuthority('PERM_ASSISTANT_ATTENDANCE')")
    @Operation(summary = "One assistant's attended sessions, newest first")
    public List<AssistantAttendanceRecordResponse> assistantAttendance(@PathVariable UUID userId) {
        return financeService.assistantAttendanceLog(userId);
    }
}
