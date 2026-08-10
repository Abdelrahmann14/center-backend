package com.center.exam.service;
import com.center.grade.entity.Grade;

import java.util.List;
import java.util.UUID;

import com.center.exam.dto.StudentExamSubmitRequest;
import com.center.exam.dto.StudentExamDetail;
import com.center.exam.dto.StudentExamResult;
import com.center.exam.dto.StudentExamSummary;

/**
 * The student side of Lesson Exams: list available exams, download one to take,
 * submit it for authoritative grading. Runs under the student's own tenant (bound
 * from their token), so the exam, roster and registration are all in scope.
 */
public interface StudentExamService {

    /** Published exams available to this student's group (with attempt status). */
    List<StudentExamSummary> available(UUID studentUserId);

    /** The full exam to take (questions, choices, password) - cached for offline. */
    StudentExamDetail open(UUID examId, UUID studentUserId);

    /** Grade and record a submission (idempotent per student+exam). */
    StudentExamResult submit(UUID examId, UUID studentUserId, StudentExamSubmitRequest request);

    /** Offline-sync replay of a submission: own transaction, idempotent, never throws on re-delivery. */
    StudentExamResult submitOffline(UUID examId, UUID studentUserId, StudentExamSubmitRequest request);

    /** The graded result of a submitted attempt, for the review screen. */
    StudentExamResult result(UUID examId, UUID studentUserId);
}
