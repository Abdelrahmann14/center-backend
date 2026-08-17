package com.center.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A student's full academic history, from their first recorded attendance
 * onwards. {@code hasData} is false for a student who has never attended a
 * lesson - the UI shows an empty state rather than zeroed statistics.
 */
public record StudentAnalyticsResponse(
        boolean hasData,
        Summary summary,
        List<Entry> timeline) {

    /** Headline figures for the stat cards. */
    public record Summary(
            /** First recorded attendance; tracking starts here. */
            LocalDate firstAttendance,
            LocalDate lastAttendance,
            long attendedLessons,
            /** Group lessons held since the first attendance with no present row. */
            long missedLessons,
            /** attended / (attended + missed), 0-100, one decimal. */
            BigDecimal attendancePercent,
            long examsTaken,
            long examsMissed,
            /** Mean of every graded exam, as a percentage of its max. */
            BigDecimal averageExamPercent,
            BigDecimal bestExamPercent,
            BigDecimal worstExamPercent,
            long homeworkIssues,
            /** Consecutive attended lessons ending at the most recent one. */
            long currentStreak,
            long longestStreak) {}

    /** One lesson in the student's history, attended or missed. */
    public record Entry(
            UUID lectureId,
            String lectureName,
            LocalDate date,
            /** Exact moment attendance was recorded; null when missed. */
            OffsetDateTime attendedAt,
            String groupName,
            boolean attended,
            String examName,
            /** False = the lesson had no exam, so no score was ever expected. */
            boolean hasExam,
            boolean examTaken,
            BigDecimal examScore,
            BigDecimal examMaxScore,
            /** Score as a percentage of the max, when both are known. */
            BigDecimal examPercent,
            String homeworkFlag) {}
}
