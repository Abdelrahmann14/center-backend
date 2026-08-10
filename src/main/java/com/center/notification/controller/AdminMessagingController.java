package com.center.notification.controller;

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

import com.center.notification.dto.AdminBroadcastRequest;
import com.center.notification.dto.BroadcastResult;
import com.center.notification.dto.MessageTemplateCreateRequest;
import com.center.notification.dto.MessageTemplateResponse;
import com.center.notification.dto.MessageTemplateUpdateRequest;
import com.center.notification.dto.MessagingRecipient;
import com.center.notification.dto.OutgoingMessageResponse;
import com.center.notification.service.AdminMessagingService;
import com.center.notification.service.VariableCatalog.Variable;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * An admin's own tenant-scoped notifications + messages. Gated by the delegatable
 * {@code NOTIFICATION_SEND} permission, so an admin may hand it to an assistant.
 */
@RestController
@RequestMapping("/api/messaging")
@PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
@RequiredArgsConstructor
@Tag(name = "Admin Messaging")
public class AdminMessagingController {

    private final AdminMessagingService service;

    @PostMapping("/notifications")
    public BroadcastResult send(@Valid @RequestBody AdminBroadcastRequest request) {
        return service.broadcast(request);
    }

    @GetMapping("/outgoing")
    public List<OutgoingMessageResponse> outgoing() {
        return service.outgoing();
    }

    @DeleteMapping("/outgoing/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOutgoing(@PathVariable UUID id) {
        service.deleteOutgoing(id);
    }

    @GetMapping("/templates")
    public List<MessageTemplateResponse> templates() {
        return service.templates();
    }

    @PostMapping("/templates")
    public MessageTemplateResponse create(@Valid @RequestBody MessageTemplateCreateRequest request) {
        return service.createTemplate(request);
    }

    @PutMapping("/templates/{code}")
    public MessageTemplateResponse update(@PathVariable String code,
            @Valid @RequestBody MessageTemplateUpdateRequest request) {
        return service.updateTemplate(code, request);
    }

    @DeleteMapping("/templates/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable String code) {
        service.deleteTemplate(code);
    }

    @PostMapping("/templates/{code}/enable")
    public MessageTemplateResponse enable(@PathVariable String code) {
        return service.setTemplateEnabled(code, true);
    }

    @PostMapping("/templates/{code}/disable")
    public MessageTemplateResponse disable(@PathVariable String code) {
        return service.setTemplateEnabled(code, false);
    }

    @GetMapping("/variables")
    public List<Variable> variables() {
        return service.variables();
    }

    @GetMapping("/students/search")
    public List<MessagingRecipient> searchStudents(@RequestParam("q") String q) {
        return service.searchStudents(q);
    }

    @GetMapping("/parents/search")
    public List<MessagingRecipient> searchParents(@RequestParam("q") String q) {
        return service.searchParents(q);
    }
}
