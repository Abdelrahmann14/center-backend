package com.center.analytics.dto;

/**
 * How many lessons a grade has, for the lectures filter tabs.
 *
 * @param grade null groups lessons with no grade set
 */
public record GradeCountResponse(String grade, long count) {
}
