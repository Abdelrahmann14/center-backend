package com.center.group.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.group.dto.GroupRequest;
import com.center.group.dto.UpdateGroupActiveRequest;
import com.center.group.dto.GroupResponse;
import com.center.group.service.GroupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Lookup data: returned as a plain list because it populates selects. */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    @Operation(summary = "List all groups with student counts and last attendance")
    public List<GroupResponse> list() {
        return groupService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@Valid @RequestBody GroupRequest request) {
        return groupService.create(request);
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    public GroupResponse update(@PathVariable UUID groupId, @Valid @RequestBody GroupRequest request) {
        return groupService.update(groupId, request);
    }

    @PatchMapping("/{groupId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Toggle a group's active flag")
    public GroupResponse setActive(@PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupActiveRequest request) {
        return groupService.setActive(groupId, request.isActive());
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a group, transferring its students to another group")
    public void delete(@PathVariable UUID groupId,
            @RequestParam(name = "transfer_to_group_id", required = false) UUID transferToGroupId) {
        groupService.delete(groupId, transferToGroupId);
    }
}
