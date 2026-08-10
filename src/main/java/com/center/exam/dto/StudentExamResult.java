package com.center.exam.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The graded result of an attempt: final score plus a per-question breakdown. */
public record StudentExamResult(
        UUID examId,
        String examName,
        BigDecimal score,
        BigDecimal bonusScore,
        BigDecimal maxScore,
        OffsetDateTime submittedAt,
        List<StudentExamQuestionResult> questions) {
}
