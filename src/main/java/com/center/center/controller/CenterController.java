package com.center.center.controller;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.center.dto.CenterRequest;
import com.center.center.dto.CenterResponse;
import com.center.center.service.CenterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Lookup data: returned as a plain list because it populates selects. */
@RestController
@RequestMapping("/api/centers")
@RequiredArgsConstructor
@Tag(name = "Centers")
public class CenterController {

    private final CenterService centerService;

    @GetMapping
    @Operation(summary = "List all centers with their per-grade prices")
    public List<CenterResponse> list() {
        return centerService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CenterResponse create(@Valid @RequestBody CenterRequest request) {
        return centerService.create(request);
    }

    @PutMapping("/{centerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public CenterResponse update(@PathVariable UUID centerId, @Valid @RequestBody CenterRequest request) {
        return centerService.update(centerId, request);
    }

    @DeleteMapping("/{centerId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID centerId) {
        centerService.delete(centerId);
    }
}
