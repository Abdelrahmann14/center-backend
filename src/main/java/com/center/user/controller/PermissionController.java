package com.center.user.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.center.user.dto.PermissionModuleResponse;
import com.center.user.service.PermissionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** The admin-facing RBAC catalog: what permissions this admin can assign. */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/catalog")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assignable permissions grouped by module, for the current admin")
    public List<PermissionModuleResponse> catalog() {
        return permissionService.catalog();
    }
}
