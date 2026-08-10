package com.center.student.dto;

import java.util.Map;

/**
 * Live duplicate feedback for the student form, so the user is warned while
 * typing instead of only being rejected on submit. Advisory only - the create
 * and update endpoints enforce the same rules regardless.
 *
 * @param phoneOwners phone digits -> the name of the student already using it
 */
public record StudentDuplicateResponse(boolean nameTaken, Map<String, String> phoneOwners) {
}
