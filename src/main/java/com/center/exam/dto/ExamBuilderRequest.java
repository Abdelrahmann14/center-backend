package com.center.exam.dto;

import java.util.List;

import jakarta.validation.Valid;

/**
 * The full builder save: exam-level settings plus the whole question set (replaces
 * the previous). Save is draft-friendly and never rejects an incomplete exam, so
 * the list may be empty; blank questions/choices are cleaned server-side and
 * publishability (`complete`) is recomputed from what remains.
 */
public record ExamBuilderRequest(
        String labelStyle,
        boolean allowMultipleCorrect,
        boolean notesEnabled,
        boolean bonusEnabled,
        @Valid List<ExamQuestionInput> questions) {
}
