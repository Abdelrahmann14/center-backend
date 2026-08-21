package com.center.admin.controller;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
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

import com.center.admin.dto.CreateAdminRequest;
import com.center.admin.dto.ModuleToggleRequest;
import com.center.admin.dto.PhotoUploadRequest;
import com.center.admin.dto.UpdateAdminRequest;
import com.center.admin.dto.AdminModuleResponse;
import com.center.admin.dto.AdminSummaryResponse;
import com.center.admin.dto.AssistantAdminResponse;
import com.center.admin.service.SuperAdminModuleService;
import com.center.admin.service.SuperAdminService;
import com.center.whatsapp.dto.WhatsappLabelRequest;
import com.center.whatsapp.dto.WhatsappAvailabilityResponse;
import com.center.whatsapp.dto.WhatsappResponsibilityAssignRequest;
import com.center.whatsapp.dto.WhatsappResponsibilityResponse;
import com.center.whatsapp.dto.WhatsappStatusResponse;
import com.center.whatsapp.service.WhatsappAvailabilityService;
import com.center.whatsapp.service.WhatsappInstanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Platform administration. Every endpoint is super-admin only; the Admin's own
 * workspace is reached separately through the normal APIs plus the
 * {@code X-Act-As-Admin} header.
 */
@RestController
@RequestMapping("/api/super")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Super Admin")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final SuperAdminModuleService superAdminModuleService;
    private final WhatsappInstanceService whatsapp;
    private final WhatsappAvailabilityService whatsappAvailability;

    @GetMapping("/admins")
    @Operation(summary = "List every Admin with workspace counts")
    public List<AdminSummaryResponse> list(@RequestParam(name = "q", required = false) String q) {
        return superAdminService.listAdmins(q);
    }

    @GetMapping("/admins/{adminId}/assistants")
    @Operation(summary = "One teacher's assistant accounts")
    public List<AssistantAdminResponse> assistants(@PathVariable UUID adminId) {
        return superAdminService.listAssistants(adminId);
    }

    @GetMapping("/admins/{adminId}")
    public AdminSummaryResponse get(@PathVariable UUID adminId) {
        return superAdminService.getAdmin(adminId);
    }

    @PutMapping("/admins/{adminId}/whatsapp-sync")
    @Operation(summary = "Enable or disable the WhatsApp feature for one Admin")
    public void setWhatsappSync(@PathVariable UUID adminId, @Valid @RequestBody ModuleToggleRequest request) {
        superAdminService.setWhatsappSync(adminId, request.enabled());
    }

    // Each Admin's numbers live on the platform's official WhatsApp account and
    // are provisioned from the Cloud endpoints, which own the whole add ->
    // verify -> register sequence. What is left here is naming a number and
    // deciding which kind of message it carries.

    @GetMapping("/admins/{adminId}/whatsapp")
    @Operation(summary = "One Admin's WhatsApp numbers")
    public List<WhatsappStatusResponse> adminWhatsapp(@PathVariable UUID adminId) {
        return whatsapp.list(adminId);
    }

    @PutMapping("/admins/{adminId}/whatsapp/{id}/label")
    @Operation(summary = "Rename one of an Admin's WhatsApp numbers")
    public WhatsappStatusResponse renameAdminWhatsapp(@PathVariable UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody WhatsappLabelRequest request) {
        return whatsapp.rename(adminId, id, request.label());
    }

    /**
     * One Admin's message types: which number carries each, and whether each can
     * be sent at all. The same view the Admin has of their own workspace, so a
     * support call ("ليه رسايل الغياب مش بتخرج؟") is answered from the same facts
     * the Admin is looking at rather than from a second, differently-computed one.
     */
    @GetMapping("/admins/{adminId}/whatsapp/availability")
    @Operation(summary = "What one Admin can send right now, and through which number")
    public WhatsappAvailabilityResponse adminWhatsappAvailability(@PathVariable UUID adminId) {
        return whatsappAvailability.availability(adminId);
    }

    @GetMapping("/admins/{adminId}/whatsapp/responsibilities")
    @Operation(summary = "One Admin's message types with their assigned numbers")
    public List<WhatsappResponsibilityResponse> adminWhatsappResponsibilities(
            @PathVariable UUID adminId) {
        return whatsappAvailability.messageTypes(adminId);
    }

    @PutMapping("/admins/{adminId}/whatsapp/responsibilities/{code}")
    @Operation(summary = "Assign one of an Admin's message types to one of their numbers")
    public List<WhatsappResponsibilityResponse> assignAdminWhatsapp(@PathVariable UUID adminId,
            @PathVariable String code, @RequestBody WhatsappResponsibilityAssignRequest request) {
        whatsapp.assign(adminId, code, request.instanceId());
        return whatsappAvailability.messageTypes(adminId);
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new Admin (a fresh isolated workspace)")
    public AdminSummaryResponse create(@Valid @RequestBody CreateAdminRequest request) {
        return superAdminService.createAdmin(request);
    }

    @PutMapping("/admins/{adminId}")
    @Operation(summary = "Rename an Admin and/or reset its password")
    public AdminSummaryResponse update(@PathVariable UUID adminId,
            @Valid @RequestBody UpdateAdminRequest request) {
        return superAdminService.updateAdmin(adminId, request);
    }

    @PostMapping("/admins/{adminId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disable an Admin without deleting its data")
    public void deactivate(@PathVariable UUID adminId) {
        superAdminService.setActive(adminId, false);
    }

    @PostMapping("/admins/{adminId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Re-enable a disabled Admin")
    public void activate(@PathVariable UUID adminId) {
        superAdminService.setActive(adminId, true);
    }

    @DeleteMapping("/admins/{adminId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete an Admin and its entire workspace")
    public void delete(@PathVariable UUID adminId) {
        superAdminService.deleteAdmin(adminId);
    }

    @GetMapping("/admins/{adminId}/modules")
    @Operation(summary = "The platform modules and their enabled state for one Admin")
    public List<AdminModuleResponse> modules(@PathVariable UUID adminId) {
        return superAdminModuleService.listForAdmin(adminId);
    }

    @PutMapping("/admins/{adminId}/modules/{moduleCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Enable or disable one platform module for an Admin")
    public void setModule(@PathVariable UUID adminId, @PathVariable String moduleCode,
            @Valid @RequestBody ModuleToggleRequest request) {
        superAdminModuleService.setEnabled(adminId, moduleCode, request.enabled());
    }

    @PutMapping("/users/{userId}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set any user's profile photo (base64 data URL)")
    public void setPhoto(@PathVariable UUID userId, @Valid @RequestBody PhotoUploadRequest request) {
        superAdminService.setUserPhoto(userId, request.data());
    }

    @DeleteMapping("/users/{userId}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove any user's profile photo")
    public void clearPhoto(@PathVariable UUID userId) {
        superAdminService.clearUserPhoto(userId);
    }

    // The console does not send. There is deliberately no broadcast, no message
    // template editor and no recipient picker here: a message to a student or a
    // parent is a teacher acting inside their own workspace, under their own
    // WhatsApp number, and recorded against their own tenant. A platform-wide
    // sender would have none of that - no tenant, no number that belongs to
    // anyone, and no teacher accountable for what went out.
}
