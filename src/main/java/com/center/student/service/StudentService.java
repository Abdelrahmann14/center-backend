package com.center.student.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.center.student.dto.StudentFilter;
import com.center.student.dto.StudentRequest;
import com.center.student.dto.StudentConflictResponse;
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

    /**
     * Who else on the roster shares this student's name or numbers.
     *
     * <p>Advisory, like {@link #checkDuplicates}, but asked from the other end:
     * that one answers "is what I am typing already taken", this one answers
     * "who is the person in front of me being confused with". The attendance
     * desk needs the second and cannot compute it - it loads one student, never
     * the roster.
     */
    StudentConflictResponse conflicts(UUID studentId);

    StudentResponse create(StudentRequest request);

    StudentResponse update(UUID studentId, StudentRequest request);

    /** Records why a discounted student pays less, without touching other fields. */
    StudentResponse setDiscountReason(UUID studentId, String reason);

    /**
     * Create or update the student under an id the CLIENT chose, for replaying a
     * write made offline. Same validation and the same Google Contacts event as
     * the online paths; only the identity is supplied rather than generated, so
     * the row the device already showed its user and the row stored here are one
     * row and not two.
     */
    StudentResponse upsert(UUID studentId, StudentRequest request);

    void delete(UUID studentId);
}
