package com.center.analytics.dto;

/** @param dayOfWeek 0 = Saturday .. 6 = Friday */
public record DayCountResponse(int dayOfWeek, long count) {
}
