package com.center.whatsapp.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.whatsapp.dto.WhatsappDelayRequest;
import com.center.whatsapp.dto.WhatsappDelayResponse;
import com.center.whatsapp.dto.WhatsappInstanceRequest;
import com.center.whatsapp.dto.WhatsappResponsibilityAssignRequest;
import com.center.whatsapp.dto.WhatsappQrResponse;
import com.center.whatsapp.dto.WhatsappResponsibilityResponse;
import com.center.whatsapp.dto.WhatsappStatusResponse;
import com.center.whatsapp.service.WhatsappInstanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Super-admin integrations (Services page). The in-app WhatsApp linking flow:
 * manage a pool of numbers (add / QR / logout / remove), assign send purposes
 * (responsibilities) to numbers, all Green API driven with automatic failover.
 */
@RestController
@RequestMapping("/api/super/services")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Services")
public class ServicesController {

    private final WhatsappInstanceService whatsapp;

    // owner = null: the super admin's own pool.

    @GetMapping("/whatsapp")
    @Operation(summary = "List WhatsApp numbers with live state")
    public List<WhatsappStatusResponse> list() {
        return whatsapp.list(null);
    }

    @PostMapping("/whatsapp")
    @Operation(summary = "Add a WhatsApp number")
    public WhatsappStatusResponse add(@Valid @RequestBody WhatsappInstanceRequest req) {
        return whatsapp.add(null, req);
    }

    @DeleteMapping("/whatsapp/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a WhatsApp number (fails over its responsibilities)")
    public void remove(@PathVariable UUID id) {
        whatsapp.delete(null, id);
    }

    @GetMapping("/whatsapp/{id}/qr")
    @Operation(summary = "Poll a fresh QR code for linking")
    public WhatsappQrResponse qr(@PathVariable UUID id) {
        return whatsapp.qr(null, id);
    }

    @PostMapping("/whatsapp/{id}/logout")
    @Operation(summary = "Unlink a WhatsApp number")
    public WhatsappStatusResponse logout(@PathVariable UUID id) {
        return whatsapp.logout(null, id);
    }

    @GetMapping("/whatsapp/{id}/delay")
    @Operation(summary = "The number's send delay between messages, in seconds")
    public WhatsappDelayResponse getDelay(@PathVariable UUID id) {
        return new WhatsappDelayResponse(whatsapp.getDelaySeconds(null, id));
    }

    @PutMapping("/whatsapp/{id}/delay")
    @Operation(summary = "Set the number's send delay (Green API reboots the instance)")
    public WhatsappDelayResponse setDelay(@PathVariable UUID id, @Valid @RequestBody WhatsappDelayRequest req) {
        return new WhatsappDelayResponse(whatsapp.setDelaySeconds(null, id, req.delaySeconds()));
    }

    @GetMapping("/whatsapp/responsibilities")
    @Operation(summary = "The responsibility catalog with current assignments")
    public List<WhatsappResponsibilityResponse> responsibilities() {
        return whatsapp.responsibilities(null);
    }

    @PutMapping("/whatsapp/responsibilities/{code}")
    @Operation(summary = "Assign a responsibility to a number (null = unassign)")
    public List<WhatsappResponsibilityResponse> assign(
            @PathVariable String code,
            @RequestBody WhatsappResponsibilityAssignRequest req) {
        return whatsapp.assign(null, code, req.instanceId());
    }
}
