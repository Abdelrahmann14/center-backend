package com.center.student.controller;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.student.dto.DiscountReasonRequest;
import com.center.student.dto.StudentFilter;
import com.center.student.dto.StudentRequest;
import com.center.student.dto.StudentDuplicateResponse;
import com.center.student.dto.StudentOptionsResponse;
import com.center.student.dto.StudentResponse;
import com.center.student.service.StudentService;
import com.center.whatsapp.dto.WhatsappCheckResponse;
import com.center.whatsapp.service.WhatsappNumberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Tag(name = "Students")
public class StudentController {

    private final StudentService studentService;
    private final WhatsappNumberService whatsappNumbers;

    /**
     * The admin app's WhatsApp check. Unlike the public {@code /register} one it
     * is workspace-scoped and remembered, so a number is asked about once and
     * every later form - and every student sharing that phone - reuses it.
     */
    @GetMapping("/check-whatsapp")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Whether a phone is on WhatsApp (remembered per workspace)")
    public WhatsappCheckResponse checkWhatsapp(@RequestParam("phone") String phone) {
        return whatsappNumbers.lookup(phone);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Search students",
            description = "Paginated. Filter with search/grade/group_id/gender/active, "
                    + "page with page/size, order with sort (e.g. sort=name,asc).")
    public Page<StudentResponse> search(
            @ParameterObject StudentFilter filter,
            @ParameterObject @PageableDefault(size = 25, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return studentService.search(filter, pageable);
    }

    /** Declared before /{studentId} so "options" is not read as an id. */
    @GetMapping("/options")
    @Operation(summary = "School/city suggestions and the next serial, for the student form")
    public StudentOptionsResponse options() {
        return studentService.options();
    }

    @GetMapping("/duplicates")
    @Operation(summary = "Live duplicate check for the student form (advisory)")
    public StudentDuplicateResponse checkDuplicates(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "phones", required = false) List<String> phones,
            @RequestParam(name = "exclude_id", required = false) UUID excludeId) {
        return studentService.checkDuplicates(name, phones == null ? List.of() : phones, excludeId);
    }

    @GetMapping("/{studentId}")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_REGISTRATION_ACCESS')")
    public StudentResponse findById(@PathVariable UUID studentId) {
        return studentService.findById(studentId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_STUDENT_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse create(@Valid @RequestBody StudentRequest request) {
        return studentService.create(request);
    }

    @PutMapping("/{studentId}")
    @PreAuthorize("hasAuthority('PERM_STUDENT_UPDATE')")
    public StudentResponse update(@PathVariable UUID studentId,
            @Valid @RequestBody StudentRequest request) {
        return studentService.update(studentId, request);
    }

    @PatchMapping("/{studentId}/discount-reason")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_UPDATE','PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Record why a discounted student pays below the center's price")
    public StudentResponse setDiscountReason(@PathVariable UUID studentId,
            @Valid @RequestBody DiscountReasonRequest request) {
        return studentService.setDiscountReason(studentId, request.discountReason());
    }

    @DeleteMapping("/{studentId}")
    @PreAuthorize("hasAuthority('PERM_STUDENT_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID studentId) {
        studentService.delete(studentId);
    }
}
