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

import com.center.student.service.StudentBarcodeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * The student's barcode card - a PDF of their basic details plus a Code128
 * barcode of their student code. Viewing/printing needs only student view;
 * sending it out on WhatsApp needs the same permission as the report.
 */
@RestController
@RequestMapping("/api/students/{studentId}/barcode")
@RequiredArgsConstructor
@Tag(name = "Student barcode")
public class StudentBarcodeController {

    private final StudentBarcodeService barcodeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "The student's barcode card as a PDF")
    public ResponseEntity<byte[]> card(@PathVariable UUID studentId) {
        byte[] pdf = barcodeService.renderPdf(studentId);
        String name = barcodeService.fileName(studentId);
        // The file name is Arabic, so it can only travel in the RFC 5987 form.
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .body(pdf);
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('PERM_STUDENT_REPORT_SEND')")
    @Operation(summary = "Send the barcode card to the student's WhatsApp")
    public Map<String, String> send(@PathVariable UUID studentId) {
        return Map.of("phone", barcodeService.send(studentId));
    }
}
