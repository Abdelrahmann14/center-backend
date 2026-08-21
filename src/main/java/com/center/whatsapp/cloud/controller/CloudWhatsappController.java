package com.center.whatsapp.cloud.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.whatsapp.cloud.dto.CloudCodeRequest;
import com.center.whatsapp.cloud.dto.CloudNumberRequest;
import com.center.whatsapp.cloud.dto.CloudNumberResponse;
import com.center.whatsapp.cloud.dto.CloudRegisterRequest;
import com.center.whatsapp.cloud.dto.CloudSendResultResponse;
import com.center.whatsapp.cloud.dto.CloudTemplateImportRequest;
import com.center.whatsapp.cloud.dto.CloudTemplateMappingRequest;
import com.center.whatsapp.cloud.dto.CloudTemplateResponse;
import com.center.whatsapp.cloud.dto.CloudTestSendRequest;
import com.center.whatsapp.cloud.dto.CloudTypeTemplateRequest;
import com.center.whatsapp.cloud.dto.CloudTypeTemplateResponse;
import com.center.whatsapp.cloud.dto.CloudVerifyRequest;
import com.center.whatsapp.cloud.service.CloudNumberService;
import com.center.whatsapp.cloud.service.CloudTemplateService;
import com.center.notification.service.VariableCatalog;
import com.center.notification.service.VariableCatalog.Variable;
import com.center.whatsapp.service.WhatsappAvailabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The official WhatsApp account, managed by the super admin only.
 *
 * <p>The platform holds the credentials and provisions a number FOR each teacher.
 * Meta's token is one secret for the whole business, so a teacher who could reach
 * these endpoints could act on every other teacher's number - which is why none
 * of this is exposed to them, not even read-only.
 */
@RestController
@RequestMapping("/api/super/whatsapp/cloud")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Cloud API")
public class CloudWhatsappController {

    private final CloudNumberService numbers;
    private final CloudTemplateService templates;
    private final WhatsappAvailabilityService availability;

    @GetMapping("/numbers")
    @Operation(summary = "Every number on the official account, with Meta's live view")
    public List<CloudNumberResponse> list() {
        return numbers.list();
    }

    @PostMapping("/numbers/refresh")
    @Operation(summary = "Pull quality, display name and status from Meta into the rows")
    public List<CloudNumberResponse> refresh() {
        return numbers.refresh();
    }

    @PostMapping("/numbers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a number to the official account, for one teacher")
    public CloudNumberResponse add(
            @RequestParam(name = "adminId", required = false) UUID adminId,
            @Valid @RequestBody CloudNumberRequest request) {
        return numbers.add(adminId, request);
    }

    @PostMapping("/numbers/import")
    @Operation(summary = "Adopt numbers that already exist on the account but are unknown here")
    public List<CloudNumberResponse> importExisting(
            @RequestParam(name = "adminId", required = false) UUID adminId) {
        return numbers.importExisting(adminId);
    }

    @PostMapping("/numbers/{id}/request-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Ask Meta to send the verification code to the number")
    public void requestCode(@PathVariable UUID id, @Valid @RequestBody CloudCodeRequest request) {
        numbers.requestCode(id, request.methodOrDefault());
    }

    @PostMapping("/numbers/{id}/verify")
    @Operation(summary = "Confirm the code the teacher read out")
    public CloudNumberResponse verify(@PathVariable UUID id, @Valid @RequestBody CloudVerifyRequest request) {
        return numbers.verify(id, request.code());
    }

    @PostMapping("/numbers/{id}/register")
    @Operation(summary = "Set the two-step PIN and register the number for sending")
    public CloudNumberResponse register(@PathVariable UUID id,
            @Valid @RequestBody CloudRegisterRequest request) {
        return numbers.register(id, request.pin());
    }

    @PostMapping("/numbers/{id}/test-send")
    @Operation(summary = "Send one template from this number, to prove the setup works")
    public CloudSendResultResponse testSend(@PathVariable UUID id,
            @Valid @RequestBody CloudTestSendRequest request) {
        return numbers.testSend(id, request);
    }

    @DeleteMapping("/numbers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove the number from the official account")
    public void delete(@PathVariable UUID id) {
        numbers.delete(id);
    }

    @GetMapping("/templates")
    @Operation(summary = "The mirrored message templates")
    public List<CloudTemplateResponse> templates(
            @RequestParam(name = "approvedOnly", defaultValue = "false") boolean approvedOnly) {
        return approvedOnly ? templates.approved() : templates.list();
    }

    @PostMapping("/templates/sync")
    @Operation(summary = "Re-read every template from Meta into the mirror")
    public List<CloudTemplateResponse> syncTemplates() {
        return templates.sync();
    }

    @PostMapping("/templates/import")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adopt one template by the id shown in WhatsApp Manager")
    public CloudTemplateResponse importTemplate(
            @Valid @RequestBody CloudTemplateImportRequest request) {
        return templates.importById(request.metaTemplateId(), request.label());
    }

    @PutMapping("/templates/{id}/mapping")
    @Operation(summary = "Map each placeholder to a system variable, and set who may use it")
    public CloudTemplateResponse saveMapping(@PathVariable UUID id,
            @Valid @RequestBody CloudTemplateMappingRequest request) {
        return templates.saveMapping(id, request);
    }

    /**
     * The variables a placeholder can be mapped to - the same list the message
     * composer offers, so a person picks "اسم الطالب" instead of typing a token.
     */
    @GetMapping("/variables")
    @Operation(summary = "The system variables a template placeholder can be mapped to")
    public List<Variable> variables() {
        return VariableCatalog.ALL;
    }

    /**
     * Which template carries which message type. With no {@code adminId} this is
     * the platform's own mapping, which every teacher inherits until they are
     * given one of their own.
     */
    @GetMapping("/message-types")
    @Operation(summary = "The template behind each message type, for one scope")
    public List<CloudTypeTemplateResponse> messageTypes(
            @RequestParam(name = "adminId", required = false) UUID adminId) {
        return templates.typeTemplates(adminId, availability.templateMapping(adminId));
    }

    @PutMapping("/message-types/{code}")
    @Operation(summary = "Point one message type at one approved template")
    public List<CloudTypeTemplateResponse> assignType(@PathVariable String code,
            @RequestParam(name = "adminId", required = false) UUID adminId,
            @Valid @RequestBody CloudTypeTemplateRequest request) {
        templates.assignType(adminId, code, request);
        return templates.typeTemplates(adminId, availability.templateMapping(adminId));
    }
}
