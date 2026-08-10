package com.center.student.service;

import java.util.UUID;

import com.center.student.dto.StudentAnalyticsResponse;

public interface StudentAnalyticsService {

    /** The student's full academic history, empty when they never attended. */
    StudentAnalyticsResponse analytics(UUID studentId);
}
