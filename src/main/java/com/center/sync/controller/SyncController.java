package com.center.sync.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.center.sync.dto.SyncPullResponse;
import com.center.sync.dto.SyncPushRequest;
import com.center.sync.dto.SyncPushResponse;
import com.center.sync.service.SyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Offline sync. Scoping comes from the caller's tenant, never from the request
 * body. Push accepts staff writes (attendance) and student exam submissions; the
 * push handler decides what each role may write. Pull streams a tenant's change
 * feed and stays staff-only - a student must never receive tenant-wide data.
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Tag(name = "Sync")
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/push")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Apply a batch of queued offline writes idempotently")
    public SyncPushResponse push(@Valid @RequestBody SyncPushRequest request) {
        return syncService.push(request);
    }

    @GetMapping("/pull")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "One page of the tenant's change feed after the given cursor")
    public SyncPullResponse pull(
            @RequestParam(value = "since", required = false) String since,
            @RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        return syncService.pull(since, limit);
    }
}
