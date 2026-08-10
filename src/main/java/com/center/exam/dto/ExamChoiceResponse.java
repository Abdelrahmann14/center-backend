package com.center.exam.dto;

import java.util.UUID;

public record ExamChoiceResponse(UUID id, String label, String text, boolean correct, int position) {
}
