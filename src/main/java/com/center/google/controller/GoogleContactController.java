package com.center.google.controller;

import java.util.List;
import java.util.Map;
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

import com.center.google.dto.GoogleConnectRequest;
import com.center.google.dto.GoogleMarkRequest;
import com.center.google.dto.GoogleMarkResponse;
import com.center.google.dto.GoogleResyncResult;
import com.center.google.dto.GoogleStatusResponse;
import com.center.google.service.GoogleContactService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin-only Google Contacts management. Assistants (USER) are excluded by
 * {@code hasRole('ADMIN')}; the current admin is the request tenant.
 */
@RestController
@RequestMapping("/api/google")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Google Contacts")
public class GoogleContactController {

    private final GoogleContactService service;

    @GetMapping("/status")
    public GoogleStatusResponse status() {
        return service.status();
    }

    @GetMapping("/oauth-url")
    @Operation(summary = "The Google consent URL to connect an account")
    public Map<String, String> oauthUrl() {
        return Map.of("url", service.authUrl());
    }

    @PostMapping("/connect")
    @Operation(summary = "Exchange the OAuth code and connect the account")
    public GoogleStatusResponse connect(@Valid @RequestBody GoogleConnectRequest req) {
        return service.connect(req.code());
    }

    @DeleteMapping("/accounts/{id}")
    @Operation(summary = "Disconnect a Google account")
    public GoogleStatusResponse disconnect(@PathVariable UUID id) {
        return service.disconnect(id);
    }

    @PostMapping("/resync")
    @Operation(summary = "Force a full re-sync of all students now")
    public GoogleResyncResult resync() {
        return service.resyncAll();
    }

    @GetMapping("/marks")
    @Operation(summary = "Per-grade contact marks (student / parent / both)")
    public List<GoogleMarkResponse> marks() {
        return service.marks();
    }

    @PutMapping("/marks/{gradeId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Set the three contact marks for one grade")
    public GoogleMarkResponse setMark(@PathVariable UUID gradeId, @Valid @RequestBody GoogleMarkRequest req) {
        return service.setMark(gradeId, req);
    }
}
