package com.center.student.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.center.student.dto.StudentAnalyticsResponse;
import com.center.student.service.StudentAnalyticsService;
import com.center.student.service.StudentReportService;
import com.center.student.service.StudentReportService.Recipient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * A single student's academic history, and the PDF report built from it.
 * Gated on the same permission as viewing students.
 */
@RestController
@RequestMapping("/api/students/{studentId}/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_STUDENT_ANALYTICS')")
@Tag(name = "Student analytics")
public class StudentAnalyticsController {

    private final StudentAnalyticsService analyticsService;
    private final StudentReportService reportService;

    @GetMapping
    @Operation(summary = "A student's attendance and exam history",
            description = "Empty (has_data=false) when the student has never attended a lesson.")
    public StudentAnalyticsResponse analytics(@PathVariable UUID studentId) {
        return analyticsService.analytics(studentId);
    }

    @GetMapping("/report")
    @Operation(summary = "The analytics report as a PDF named after the student")
    public ResponseEntity<byte[]> report(@PathVariable UUID studentId) {
        byte[] pdf = reportService.renderPdf(studentId);
        String name = reportService.fileName(studentId);
        // The file name is Arabic, so it can only travel in the RFC 5987 form.
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(pdf);
    }

    @PostMapping("/report/send/parent")
    @PreAuthorize("hasAuthority('PERM_STUDENT_REPORT_SEND')")
    @Operation(summary = "Send the report to the parent's WhatsApp")
    public Map<String, String> sendToParent(@PathVariable UUID studentId) {
        return Map.of("phone", reportService.send(studentId, Recipient.PARENT));
    }

    @PostMapping("/report/send/student")
    @PreAuthorize("hasAuthority('PERM_STUDENT_REPORT_SEND')")
    @Operation(summary = "Send the report to the student's WhatsApp")
    public Map<String, String> sendToStudent(@PathVariable UUID studentId) {
        return Map.of("phone", reportService.send(studentId, Recipient.STUDENT));
    }
}
