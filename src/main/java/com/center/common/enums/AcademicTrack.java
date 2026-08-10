package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * students.academic_track (الشعبة). Which values are offered depends on the
 * grade's {@link TrackKind} - see src/lib/tracks.ts, the UI's source of truth.
 */
public enum AcademicTrack implements PersistableEnum {

    /** g11 only. */
    SCIENCE("علمي"),
    /** g11 and g12. */
    LITERARY("أدبي"),
    /** g12 only. */
    SCIENCE_BIOLOGY("علمي علوم"),
    /** g12 only. */
    SCIENCE_MATH("علمي رياضة");

    private final String value;

    AcademicTrack(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AcademicTrack fromValue(String value) {
        return PersistableEnum.fromValue(AcademicTrack.class, value);
    }
}
