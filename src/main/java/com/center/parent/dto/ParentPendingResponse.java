package com.center.parent.dto;

/**
 * Shown after a link request is submitted: the request is now waiting on the
 * named student's approval.
 */
public record ParentPendingResponse(String studentName) {
}
