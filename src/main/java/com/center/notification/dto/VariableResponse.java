package com.center.notification.dto;

/**
 * A message variable as the composer sees it. {@code label} is the two or three
 * plain words shown on the chip; the {@code key} travels only so the editor can
 * store and re-read the token, and is never displayed.
 */
public record VariableResponse(String key, String label, String description, String group,
        String example) {
}
