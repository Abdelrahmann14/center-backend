package com.center.student.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.center.student.dto.StudentFilter;
import com.center.student.dto.StudentRequest;
import com.center.student.dto.StudentDuplicateResponse;
import com.center.student.dto.StudentOptionsResponse;
import com.center.student.dto.StudentResponse;

public interface StudentService {

    Page<StudentResponse> search(StudentFilter filter, Pageable pageable);

    /** Suggestion lists and the next serial, for the student form. */
    StudentOptionsResponse options();

    /**
     * Advisory duplicate check driving the form's live warnings.
     *
     * @param excludeId the student being edited, so it never clashes with itself
     */
    StudentDuplicateResponse checkDuplicates(String name, List<String> phones, UUID excludeId);

    StudentResponse findById(UUID studentId);

    StudentResponse create(StudentRequest request);

    StudentResponse update(UUID studentId, StudentRequest request);

    void delete(UUID studentId);
}
