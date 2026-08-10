package com.center.analytics.dto;

import java.util.List;

public record AnalyticsResponse(
        long assistantsCount,
        long studentsCount,
        long groupsCount,
        long centersCount,
        List<DayCountResponse> groupsByDay) {
}
