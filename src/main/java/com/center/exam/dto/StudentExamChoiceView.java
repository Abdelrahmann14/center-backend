package com.center.exam.dto;

import java.util.UUID;

/**
 * A choice as shown to a student. {@code correct} is included so the exam can be
 * graded and reviewed on-device (offline-first); the password screen gates access.
 */
public record StudentExamChoiceView(UUID id, String label, String text, boolean correct) {
}
