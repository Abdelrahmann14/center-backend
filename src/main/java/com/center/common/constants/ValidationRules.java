package com.center.common.constants;

/** Field limits shared by the request DTOs and the domain services. */
public final class ValidationRules {

    public static final int USERNAME_MAX = 50;
    public static final int PASSWORD_MIN = 4;
    public static final int PASSWORD_MAX = 128;

    public static final int STUDENT_NAME_MAX = 120;
    public static final int GRADE_NAME_MAX = 80;
    public static final int CENTER_NAME_MAX = 120;
    public static final int LECTURE_NAME_MAX = 160;
    public static final int NOTES_MAX = 2000;

    /** Egyptian mobile numbers: exactly 11 digits, at most three per person. */
    public static final int PHONE_DIGITS = 11;
    public static final int MAX_PHONES = 3;

    /** "HH:mm" in 24-hour form. */
    public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    /** Arabic-letter words separated by single spaces. */
    public static final String ARABIC_NAME_PATTERN = "^[\\u0621-\\u064A]+( [\\u0621-\\u064A]+)*$";

    private ValidationRules() {
    }
}
