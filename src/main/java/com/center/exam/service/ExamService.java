package com.center.exam.service;

import java.util.List;
import java.util.UUID;

import com.center.exam.dto.ExamBuilderRequest;
import com.center.exam.dto.ExamRequest;
import com.center.exam.dto.ExamScheduleRequest;
import com.center.exam.dto.ExamDetailResponse;
import com.center.exam.dto.ExamResponse;

/**
 * Lesson Exams (admin-only). An exam is anchored to a lesson; its name and max
 * score are kept in sync with the lesson's {@code exam_name}/{@code exam_grade}.
 */
public interface ExamService {

    /** Every exam in the tenant, ordered by stage so the client can group them. */
    List<ExamResponse> list();

    /** One exam with its full question/choice tree, for the builder. */
    ExamDetailResponse get(UUID examId);

    /** Create an exam for a lesson (grade auto-copied; name/score written back). */
    ExamResponse create(ExamRequest request);

    /** Edit an exam's name/score/duration (name/score written back to the lesson). */
    ExamResponse update(UUID examId, ExamRequest request);

    /** Replace the exam's questions and choices from the builder. */
    ExamDetailResponse saveQuestions(UUID examId, ExamBuilderRequest request);

    /** Assign groups and a date (generates the fixed exam password on first schedule). */
    ExamResponse schedule(UUID examId, ExamScheduleRequest request);

    /** Publish a complete, scheduled exam to its students (notifies each of them). */
    ExamResponse publish(UUID examId);

    void delete(UUID examId);
}
