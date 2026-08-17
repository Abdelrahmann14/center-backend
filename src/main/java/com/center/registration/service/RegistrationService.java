package com.center.registration.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.center.registration.dto.CreateRegistrationRequest;
import com.center.registration.dto.RegistrationFilter;
import com.center.registration.dto.UpdateHomeworkRequest;
import com.center.lecture.dto.LessonGroupResponse;
import com.center.lecture.dto.LessonHistoryResponse;
import com.center.analytics.dto.PriceBucketResponse;
import com.center.registration.dto.RegistrationResponse;

public interface RegistrationService {

    Page<RegistrationResponse> search(RegistrationFilter filter, Pageable pageable);

    /** Distinct groups that attended a lesson, with head counts. */
    List<LessonGroupResponse> lessonGroups(UUID lectureId);

    /** A lesson's present students aggregated by the amount they paid. */
    List<PriceBucketResponse> statsByPrice(UUID lectureId);

    /** Every lesson of the student's grade; unregistered ones read as absent. */
    List<LessonHistoryResponse> historyForStudent(UUID studentId);

    RegistrationResponse register(CreateRegistrationRequest request);

    RegistrationResponse updateHomework(UUID registrationId, UpdateHomeworkRequest request);

    /** @param examScore null clears it; otherwise 0..the lesson's maximum */
    RegistrationResponse updateExamScore(UUID registrationId, BigDecimal examScore);

    /**
     * Replay a registration written offline, under the id the CLIENT chose.
     *
     * <p>Registrations carry a natural key - one row per (lesson, student) -
     * that two devices compute identically, so a replay collapses onto the row
     * that already owns that pair instead of racing it. That makes this the one
     * entity here where two people registering the same student cannot conflict.
     * Homework and exam score ride along, because sync sends whole rows and
     * there is no offline equivalent of the two field-level PATCH endpoints.
     */
    RegistrationResponse upsert(UUID registrationId, CreateRegistrationRequest request,
            BigDecimal examScore);

    void unregister(UUID registrationId);
}
