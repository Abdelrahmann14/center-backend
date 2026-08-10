package com.center.exam.dto;

import java.util.List;
import java.util.UUID;

/** A student's selected choices for one question. */
public record StudentAnswerInput(UUID questionId, List<UUID> choiceIds) {
}
