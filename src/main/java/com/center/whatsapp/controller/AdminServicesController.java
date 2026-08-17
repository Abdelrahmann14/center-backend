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
import com.center.common.exception.BusinessRuleException;
import com.center.whatsapp.service.WhatsappInstanceService;
import com.center.common.tenant.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The admin's own WhatsApp number pool (mirror of the super-admin Services page,
 * scoped to the admin's workspace). Assistants (USER) are excluded; the owner is
 * the request tenant.
 */
@RestController
@RequestMapping("/api/services")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Services")
public class AdminServicesController {

    private final WhatsappInstanceService whatsapp;

    private UUID owner() {
        UUID id = TenantContext.get();
        if (id == null) {
            throw new BusinessRuleException("هذه الصفحة متاحة لحسابات المدرّسين فقط");
        }
        return id;
    }

    @GetMapping("/whatsapp/status")
    @Operation(summary = "Whether the super admin has enabled WhatsApp for this admin")
    public java.util.Map<String, Boolean> status() {
        return java.util.Map.of("enabled", whatsapp.enabledFor(owner()));
    }

    @GetMapping("/whatsapp")
    public List<WhatsappStatusResponse> list() {
        return whatsapp.list(owner());
    }

    @PostMapping("/whatsapp")
    public WhatsappStatusResponse add(@Valid @RequestBody WhatsappInstanceRequest req) {
        return whatsapp.add(owner(), req);
    }

    @DeleteMapping("/whatsapp/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        whatsapp.delete(owner(), id);
    }

    @GetMapping("/whatsapp/{id}/qr")
    public WhatsappQrResponse qr(@PathVariable UUID id) {
        return whatsapp.qr(owner(), id);
    }

    @PostMapping("/whatsapp/{id}/logout")
    public WhatsappStatusResponse logout(@PathVariable UUID id) {
        return whatsapp.logout(owner(), id);
    }

    @GetMapping("/whatsapp/{id}/delay")
    @Operation(summary = "The number's send delay between messages, in seconds")
    public WhatsappDelayResponse getDelay(@PathVariable UUID id) {
        return new WhatsappDelayResponse(whatsapp.getDelaySeconds(owner(), id));
    }

    @PutMapping("/whatsapp/{id}/delay")
    @Operation(summary = "Set the number's send delay (Green API reboots the instance)")
    public WhatsappDelayResponse setDelay(@PathVariable UUID id, @Valid @RequestBody WhatsappDelayRequest req) {
        return new WhatsappDelayResponse(whatsapp.setDelaySeconds(owner(), id, req.delaySeconds()));
    }

    @GetMapping("/whatsapp/responsibilities")
    public List<WhatsappResponsibilityResponse> responsibilities() {
        return whatsapp.responsibilities(owner());
    }

    @PutMapping("/whatsapp/responsibilities/{code}")
    @Operation(summary = "Assign a responsibility to one of the admin's numbers")
    public List<WhatsappResponsibilityResponse> assign(
            @PathVariable String code,
            @RequestBody WhatsappResponsibilityAssignRequest req) {
        return whatsapp.assign(owner(), code, req.instanceId());
    }
}
