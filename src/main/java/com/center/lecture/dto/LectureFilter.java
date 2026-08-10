package com.center.lecture.dto;

/** Query filters for the paginated lecture list. All fields optional. */
public record LectureFilter(
        /** Matches the lesson or exam name, case-insensitively. */
        String search,
        String grade) {
}
