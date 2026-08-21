package com.center.student.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.center.messaging.service.WhatsappMessagingService;
import com.center.messaging.service.WhatsappMessagingService.BarcodeBacklog;
import com.center.messaging.service.WhatsappMessagingService.BarcodeBatchResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

/**
 * Sending the barcode card to everyone who has never received one.
 *
 * <p>Separate from {@link StudentBarcodeController} only because that one is
 * mounted under a student id and this is not about any one student. The path is
 * a literal, so it is matched ahead of {@code /api/students/{studentId}} and is
 * never read as an id.
 *
 * <p>The send runs in batches the caller loops over rather than one long request
 * - see {@link WhatsappMessagingService#sendPendingBarcodes(int)} for why.
 */
@RestController
@RequestMapping("/api/students/barcode")
@RequiredArgsConstructor
@Tag(name = "Student barcode")
public class StudentBarcodeBatchController {

    /**
     * Cards per request. Each one is a PDF render plus an upload and a send, so
     * this is chosen to keep a request comfortably inside every timeout between
     * the sender and the browser, not to be as large as it could be.
     */
    private static final int BATCH = 20;

    private final WhatsappMessagingService messagingService;

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('PERM_STUDENT_REPORT_SEND')")
    @Operation(summary = "How many students have never been sent their barcode card")
    public BarcodeBacklog pending() {
        return messagingService.barcodeBacklog();
    }

    @PostMapping("/send-pending")
    @PreAuthorize("hasAuthority('PERM_STUDENT_REPORT_SEND')")
    @Operation(summary = "Send the card to the next batch of students who never got one",
            description = "Returns what this batch did and how many are still waiting. "
                    + "Call again while remaining > 0 and the batch still sent something.")
    public BarcodeBatchResult sendPending(
            @RequestParam(defaultValue = "" + BATCH) int limit) {
        return messagingService.sendPendingBarcodes(Math.min(limit, BATCH));
    }

    /** Whether adding a student sends them their card straight away. */
    public record AutoSend(boolean enabled) {
    }

    @GetMapping("/auto")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_STUDENT_CREATE')")
    @Operation(summary = "Does adding a student from THIS account send them the card")
    public AutoSend auto() {
        return new AutoSend(messagingService.barcodeAutoSend());
    }

    @PutMapping("/auto")
    @PreAuthorize("hasAuthority('PERM_STUDENT_CREATE')")
    @Operation(summary = "Turn the automatic send on or off for the signed-in account",
            description = "Remembered, and scoped to the caller's own account: assistants "
                    + "under one teacher each carry their own answer, so turning it on "
                    + "here changes nothing for anybody else on the workspace.")
    public AutoSend setAuto(@Valid @RequestBody AutoSend body) {
        return new AutoSend(messagingService.setBarcodeAutoSend(body.enabled()));
    }

    /** The students the teacher previewed and chose to send to. */
    public record BarcodeSelection(@NotEmpty(message = "لم يتم اختيار أي طالب") List<UUID> ids) {
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('PERM_STUDENT_REPORT_SEND')")
    @Operation(summary = "Send the card to exactly these students",
            description = "The list the caller previewed on screen. Capped per request "
                    + "for the same reason /send-pending is; send the rest in further calls.")
    public BarcodeBatchResult send(@Valid @RequestBody BarcodeSelection body) {
        // Truncated rather than rejected: a caller that over-sends gets the first
        // BATCH done and can see from `remaining` that there is more to do, which
        // is a better answer than losing the whole slice to a 400.
        List<UUID> ids = body.ids();
        return messagingService.sendBarcodes(ids.size() > BATCH ? ids.subList(0, BATCH) : ids);
    }
}
