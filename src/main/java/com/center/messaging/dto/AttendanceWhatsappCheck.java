package com.center.messaging.dto;

/**
 * Whether a student's parent number is reachable on WhatsApp, checked before a
 * toggle-on attendance registration is committed.
 *
 * <p>{@code status}:
 * <ul>
 *   <li>{@code ON} - the parent number exists on WhatsApp; the message will send.
 *   <li>{@code OFF} - the number is not on WhatsApp; the message would fail.
 *   <li>{@code NO_PHONE} - the student has no parent number at all.
 *   <li>{@code UNKNOWN} - it could not be verified (Green API not configured, or
 *       the call failed / the server is offline). The caller attends anyway and
 *       surfaces {@code detail} as the reason, never blocking on an outage.
 * </ul>
 */
public record AttendanceWhatsappCheck(String status, String detail) {}
