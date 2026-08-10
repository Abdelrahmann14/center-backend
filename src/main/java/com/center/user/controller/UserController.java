package com.center.user.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.user.dto.CreateUserRequest;
import com.center.user.dto.SetUserPermissionsRequest;
import com.center.user.dto.UpdateUserRequest;
import com.center.user.dto.AssistantResponse;
import com.center.user.dto.UserResponse;
import com.center.user.service.PermissionService;
import com.center.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;

    /** Readable by any authenticated user - the lecture forms need it. */
    @GetMapping("/assistants")
    @Operation(summary = "List assistants")
    public List<AssistantResponse> listAssistants() {
        return userService.findAssistants();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list() {
        return userService.findAll();
    }

    @GetMapping("/username-available")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Check if a username is free (globally unique)")
    public Map<String, Boolean> usernameAvailable(@RequestParam("username") String username) {
        return Map.of("available", userService.isUsernameAvailable(username));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(userId, request);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID userId) {
        userService.delete(userId);
    }

    @GetMapping("/{userId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "The permission codes granted to one assistant")
    public List<String> permissions(@PathVariable UUID userId) {
        return permissionService.userPermissions(userId);
    }

    @PutMapping("/{userId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Replace an assistant's granted permissions")
    public void setPermissions(@PathVariable UUID userId, @Valid @RequestBody SetUserPermissionsRequest request) {
        permissionService.setUserPermissions(userId, request.codesOrEmpty());
    }
}
