package com.center.whatsapp.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.center.whatsapp.dto.WhatsappAvailabilityResponse;
import com.center.whatsapp.dto.WhatsappSendingRequest;
import com.center.whatsapp.dto.WhatsappLabelRequest;
import com.center.whatsapp.dto.WhatsappResponsibilityAssignRequest;
import com.center.whatsapp.dto.WhatsappResponsibilityResponse;
import com.center.whatsapp.dto.WhatsappStatusResponse;
import com.center.whatsapp.dto.WhatsappUsageResponse;
import com.center.whatsapp.cloud.dto.CloudTemplateResponse;
import com.center.whatsapp.cloud.dto.WhatsappMessagePreview;
import com.center.whatsapp.cloud.service.CloudTemplateService;
import com.center.common.exception.BusinessRuleException;
import com.center.whatsapp.service.WhatsappAvailabilityService;
import com.center.whatsapp.service.WhatsappInstanceService;
import com.center.whatsapp.service.WhatsappUsageService;
import com.center.common.tenant.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The admin's own WhatsApp numbers, scoped to their workspace. Assistants (USER)
 * are excluded; the owner is the request tenant.
 *
 * <p>Read-mostly on purpose. The numbers live on the platform's official WhatsApp
 * account and the super admin provisions them, so the only things an admin does
 * here are name a number and say which number carries which kind of message.
 */
@RestController
@RequestMapping("/api/services")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Services")
public class AdminServicesController {

    private final WhatsappInstanceService whatsapp;
    private final WhatsappAvailabilityService availability;
    private final WhatsappUsageService usage;
    private final CloudTemplateService templates;

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

    /**
     * What this workspace can actually send right now. The single source of truth
     * the UI mirrors - see {@link WhatsappAvailabilityResponse} for why it is one
     * endpoint rather than a rule repeated on every screen.
     */
    @GetMapping("/whatsapp/availability")
    @Operation(summary = "Which WhatsApp actions are available, and through which number")
    public WhatsappAvailabilityResponse availability() {
        return availability.availability(owner());
    }

    /**
     * The workspace's own master switch for sending.
     *
     * <p>Returns the whole availability picture rather than an acknowledgement,
     * because every send button on every screen is driven by it: one response
     * repaints them all, and a second round trip to find out what just changed
     * would leave a window where the buttons disagree with the switch.
     */
    @PutMapping("/whatsapp/sending")
    @Operation(summary = "Turn this workspace's WhatsApp sending on or off")
    public WhatsappAvailabilityResponse setSending(@Valid @RequestBody WhatsappSendingRequest req) {
        whatsapp.setSending(owner(), req.enabled());
        return availability.availability(owner());
    }

    @GetMapping("/whatsapp/usage")
    @Operation(summary = "One month of WhatsApp usage: volume, numbers, and cost")
    public WhatsappUsageResponse usage(
            @RequestParam(required = false) String month) {
        return usage.usage(owner(), month);
    }

    @GetMapping("/whatsapp")
    public List<WhatsappStatusResponse> list() {
        return whatsapp.list(owner());
    }

    // The admin does NOT add, activate or remove their own numbers: the super
    // admin provisions them on the official account. All the admin does with a
    // number is name it.
    @PutMapping("/whatsapp/{id}/label")
    @Operation(summary = "Rename one of the admin's numbers")
    public WhatsappStatusResponse rename(@PathVariable UUID id, @Valid @RequestBody WhatsappLabelRequest req) {
        return whatsapp.rename(owner(), id, req.label());
    }

    /**
     * The templates this account has been given. Only the ones the super admin
     * shared with it - a template written for one centre names that centre, and
     * showing it to another would be a leak with a message attached.
     */
    @GetMapping("/whatsapp/templates")
    @Operation(summary = "The approved templates made available to this account")
    public List<CloudTemplateResponse> templates() {
        return templates.availableFor(owner());
    }

    /**
     * What each kind of message looks like when it arrives.
     *
     * <p>The teacher chooses none of this - one number, and templates the
     * platform writes - so reading the wording is the whole point of the screen
     * that calls it.
     */
    @GetMapping("/whatsapp/message-previews")
    @Operation(summary = "Each message type as the parent receives it")
    public List<WhatsappMessagePreview> messagePreviews() {
        return templates.previewsFor(owner());
    }

    @GetMapping("/whatsapp/responsibilities")
    public List<WhatsappResponsibilityResponse> responsibilities() {
        return availability.messageTypes(owner());
    }

    @PutMapping("/whatsapp/responsibilities/{code}")
    @Operation(summary = "Assign a responsibility to one of the admin's numbers")
    public List<WhatsappResponsibilityResponse> assign(
            @PathVariable String code,
            @RequestBody WhatsappResponsibilityAssignRequest req) {
        whatsapp.assign(owner(), code, req.instanceId());
        return availability.messageTypes(owner());
    }
}
