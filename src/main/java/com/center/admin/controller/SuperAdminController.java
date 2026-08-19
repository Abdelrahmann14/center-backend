package com.center.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import com.center.notification.dto.BroadcastRequest;
import com.center.admin.dto.CreateAdminRequest;
import com.center.notification.dto.MessageTemplateCreateRequest;
import com.center.notification.dto.MessageTemplateUpdateRequest;
import com.center.admin.dto.ModuleToggleRequest;
import com.center.admin.dto.PhotoUploadRequest;
import com.center.notification.dto.SenderNameRequest;
import com.center.admin.dto.SuperParentUpdateRequest;
import com.center.admin.dto.SuperStudentUpdateRequest;
import com.center.admin.dto.UpdateAdminRequest;
import com.center.admin.dto.AdminModuleResponse;
import com.center.admin.dto.AdminSummaryResponse;
import com.center.admin.dto.AssistantAdminResponse;
import com.center.notification.dto.BroadcastResult;
import com.center.notification.dto.MessageTemplateResponse;
import com.center.notification.dto.OutgoingMessageResponse;
import com.center.admin.dto.ParentAdminResponse;
import com.center.admin.dto.ParentDetailResponse;
import com.center.admin.dto.StudentAdminResponse;
import com.center.admin.dto.StudentDetailResponse;
import com.center.user.dto.UserSearchResponse;
import com.center.notification.dto.VariableResponse;
import com.center.notification.service.MessageTemplateService;
import com.center.settings.service.SettingsService;
import com.center.admin.service.SuperAdminModuleService;
import com.center.admin.service.SuperAdminService;
import com.center.notification.service.VariableCatalog;
import com.center.whatsapp.dto.WhatsappInstanceRequest;
import com.center.whatsapp.dto.WhatsappLabelRequest;
import com.center.whatsapp.dto.WhatsappStatusResponse;
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
    private final SettingsService settingsService;
    private final MessageTemplateService messageTemplateService;
    private final WhatsappInstanceService whatsapp;

    @GetMapping("/admins")
    @Operation(summary = "List every Admin with workspace counts")
    public List<AdminSummaryResponse> list(@RequestParam(name = "q", required = false) String q) {
        return superAdminService.listAdmins(q);
    }

    @GetMapping("/students")
    @Operation(summary = "Students across all workspaces (search + filters, paginated)")
    public Page<StudentAdminResponse> students(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "teacherId", required = false) UUID teacherId,
            @RequestParam(name = "grade", required = false) String grade,
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "registered", required = false) Boolean registered,
            @RequestParam(name = "active", required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        return superAdminService.listStudents(q, teacherId, grade, gender, registered, active, pageable);
    }

    @GetMapping("/student-grades")
    @Operation(summary = "Distinct grades across all workspaces (students filter)")
    public List<String> studentGrades() {
        return superAdminService.listStudentGrades();
    }

    @PutMapping("/students/{studentId}")
    @Operation(summary = "Edit a student's core fields")
    public void updateStudent(@PathVariable UUID studentId,
            @Valid @RequestBody SuperStudentUpdateRequest request) {
        superAdminService.updateStudent(studentId, request);
    }

    @DeleteMapping("/students/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete a student")
    public void deleteStudent(@PathVariable UUID studentId) {
        superAdminService.deleteStudent(studentId);
    }

    @GetMapping("/parents")
    @Operation(summary = "Parents across all workspaces (name-filtered, paginated)")
    public Page<ParentAdminResponse> parents(@RequestParam(name = "q", required = false) String q,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        return superAdminService.listParents(q, pageable);
    }

    @PutMapping("/parents/{parentId}")
    @Operation(summary = "Edit a parent's core fields")
    public void updateParent(@PathVariable UUID parentId,
            @Valid @RequestBody SuperParentUpdateRequest request) {
        superAdminService.updateParent(parentId, request);
    }

    @DeleteMapping("/parents/{parentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete a parent")
    public void deleteParent(@PathVariable UUID parentId) {
        superAdminService.deleteParent(parentId);
    }

    @GetMapping("/admins/{adminId}/assistants")
    @Operation(summary = "One teacher's assistant accounts")
    public List<AssistantAdminResponse> assistants(@PathVariable UUID adminId) {
        return superAdminService.listAssistants(adminId);
    }

    @GetMapping("/students/{studentId}")
    @Operation(summary = "One student's full profile")
    public StudentDetailResponse student(@PathVariable UUID studentId) {
        return superAdminService.getStudentDetail(studentId);
    }

    @GetMapping("/parents/{parentId}")
    @Operation(summary = "One parent's full profile")
    public ParentDetailResponse parent(@PathVariable UUID parentId) {
        return superAdminService.getParentDetail(parentId);
    }

    @PostMapping("/students/{studentId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateStudent(@PathVariable UUID studentId) {
        superAdminService.setStudentActive(studentId, false);
    }

    @PostMapping("/students/{studentId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateStudent(@PathVariable UUID studentId) {
        superAdminService.setStudentActive(studentId, true);
    }

    @PostMapping("/parents/{parentId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateParent(@PathVariable UUID parentId) {
        superAdminService.setParentActive(parentId, false);
    }

    @PostMapping("/parents/{parentId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateParent(@PathVariable UUID parentId) {
        superAdminService.setParentActive(parentId, true);
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

    // The super admin provisions each Admin's WhatsApp numbers: they hold the
    // Green API credentials and enter them here (instance id + token). The Admin
    // then only sees a card to scan the QR and a field to name the number - it
    // never sees or enters the credentials.

    @GetMapping("/admins/{adminId}/whatsapp")
    @Operation(summary = "One Admin's WhatsApp numbers with live state")
    public List<WhatsappStatusResponse> adminWhatsapp(@PathVariable UUID adminId) {
        return whatsapp.list(adminId);
    }

    @PostMapping("/admins/{adminId}/whatsapp")
    @Operation(summary = "Provision a WhatsApp number for an Admin (Green API credentials)")
    public WhatsappStatusResponse addAdminWhatsapp(@PathVariable UUID adminId,
            @Valid @RequestBody WhatsappInstanceRequest request) {
        return whatsapp.add(adminId, request);
    }

    @PutMapping("/admins/{adminId}/whatsapp/{id}/label")
    @Operation(summary = "Rename one of an Admin's WhatsApp numbers")
    public WhatsappStatusResponse renameAdminWhatsapp(@PathVariable UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody WhatsappLabelRequest request) {
        return whatsapp.rename(adminId, id, request.label());
    }

    @DeleteMapping("/admins/{adminId}/whatsapp/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove one of an Admin's WhatsApp numbers")
    public void removeAdminWhatsapp(@PathVariable UUID adminId, @PathVariable UUID id) {
        whatsapp.delete(adminId, id);
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

    @GetMapping("/users/search")
    @Operation(summary = "Name search across all accounts (notification picker)")
    public List<UserSearchResponse> searchUsers(@RequestParam("q") String q) {
        return superAdminService.searchUsers(q);
    }

    @PostMapping("/notifications")
    @Operation(summary = "Broadcast to the union of the selected recipient facets")
    public BroadcastResult broadcast(@Valid @RequestBody BroadcastRequest request) {
        return superAdminService.broadcast(request);
    }

    @GetMapping("/outgoing")
    @Operation(summary = "Recent broadcasts (History panel)")
    public List<OutgoingMessageResponse> outgoing() {
        return superAdminService.listOutgoing();
    }

    @DeleteMapping("/outgoing/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a sent broadcast and remove it from every recipient")
    public void deleteOutgoing(@PathVariable UUID id) {
        superAdminService.deleteOutgoing(id);
    }

    @GetMapping("/settings/sender-name")
    @Operation(summary = "The current notification sender name")
    public Map<String, String> senderName() {
        return Map.of("name", settingsService.senderName());
    }

    @PutMapping("/settings/sender-name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set the notification sender name")
    public void setSenderName(@Valid @RequestBody SenderNameRequest request) {
        settingsService.setSenderName(request.name());
    }

    @GetMapping("/templates")
    @Operation(summary = "Editable system-message templates")
    public List<MessageTemplateResponse> templates() {
        return messageTemplateService.list();
    }

    @PutMapping("/templates/{code}")
    @Operation(summary = "Edit one message template's text")
    public MessageTemplateResponse updateTemplate(@PathVariable String code,
            @Valid @RequestBody MessageTemplateUpdateRequest request) {
        return messageTemplateService.update(code, request);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a custom message template")
    public MessageTemplateResponse createTemplate(@Valid @RequestBody MessageTemplateCreateRequest request) {
        return messageTemplateService.create(request);
    }

    @DeleteMapping("/templates/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a custom template (system templates cannot be deleted)")
    public void deleteTemplate(@PathVariable String code) {
        messageTemplateService.delete(code);
    }

    @PostMapping("/templates/{code}/enable")
    @Operation(summary = "Enable a template")
    public MessageTemplateResponse enableTemplate(@PathVariable String code) {
        return messageTemplateService.setEnabled(code, true);
    }

    @PostMapping("/templates/{code}/disable")
    @Operation(summary = "Disable a template")
    public MessageTemplateResponse disableTemplate(@PathVariable String code) {
        return messageTemplateService.setEnabled(code, false);
    }

    @GetMapping("/variables")
    @Operation(summary = "The {placeholder} variables available to the composer")
    public List<VariableResponse> variables() {
        return VariableCatalog.ALL.stream()
                .map(v -> new VariableResponse(v.key(), v.label(), v.description(), v.group(), v.example()))
                .toList();
    }
}
