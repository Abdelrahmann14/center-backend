package com.center.exam.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * A student's exam submission. Same payload for every submit path (timer expiry,
 * finish button, early submit) and for the offline sync replay; the server grades
 * authoritatively and never trusts a client-computed score.
 */
public record StudentExamSubmitRequest(
        OffsetDateTime startedAt,
        @NotNull List<StudentAnswerInput> answers) {
}
