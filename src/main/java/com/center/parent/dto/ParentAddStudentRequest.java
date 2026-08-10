package com.center.parent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** An existing (logged-in) parent requesting a link to another student. */
public record ParentAddStudentRequest(
        @NotNull(message = "مطلوب") @Positive Integer serial) {
}
