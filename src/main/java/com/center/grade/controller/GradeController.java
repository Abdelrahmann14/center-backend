package com.center.grade.controller;

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

import com.center.grade.dto.GradeRequest;
import com.center.grade.dto.GradeResponse;
import com.center.grade.service.GradeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Lookup data: returned as a plain list because it populates selects. */
@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
@Tag(name = "Grades")
public class GradeController {

    private final GradeService gradeService;

    @GetMapping
    @Operation(summary = "List all grades (the global master list; every role may read)")
    public List<GradeResponse> list() {
        return gradeService.findAll();
    }

    /**
     * The grades this workspace teaches - the ones its centers price.
     *
     * <p>What the student form should offer. {@link #list()} stays the master
     * list because other screens need to render a grade that is no longer taught
     * (an old student still has one), while a form that offers it would be
     * inviting a mistake.
     */
    @GetMapping("/in-use")
    @Operation(summary = "Grades priced by this workspace's centers, in school order")
    public List<GradeResponse> inUse() {
        return gradeService.findInUse();
    }

    /**
     * How many students each grade holds, platform-wide.
     *
     * <p>Kept off {@link #list()} rather than folded into it: that list feeds
     * every select in the app, and a teacher reading it has no business seeing
     * totals that span other workspaces.
     */
    @GetMapping("/student-counts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Students per grade across the platform (super admin only)")
    public Map<String, Long> studentCounts() {
        return gradeService.studentCountsByGrade();
    }

    // Grades are now a global master list: only the super admin may mutate them,
    // every Admin reads the same list.
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public GradeResponse create(@Valid @RequestBody GradeRequest request) {
        return gradeService.create(request);
    }

    @PutMapping("/{gradeId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public GradeResponse update(@PathVariable UUID gradeId, @Valid @RequestBody GradeRequest request) {
        return gradeService.update(gradeId, request);
    }

    @DeleteMapping("/{gradeId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID gradeId) {
        gradeService.delete(gradeId);
    }
}
