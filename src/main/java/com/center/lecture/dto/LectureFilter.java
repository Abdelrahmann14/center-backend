package com.center.lecture.dto;

/** Query filters for the paginated lecture list. All fields optional. */
public record LectureFilter(
        /** Matches the lesson or exam name, case-insensitively. */
        String search,
        String grade,
        /** True = only lessons with an exam, false = only those without, null = both. */
        Boolean hasExam,
        /**
         * Only lessons created in the last N days, counted back from now. Null or
         * non-positive means no limit. Days rather than a date because the filter
         * the screen offers is "the last 7 days", not a calendar range.
         */
        Integer withinDays) {
}
