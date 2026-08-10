package com.center.exam.dto;

/**
 * One answer choice in the exam builder. Blank choices are dropped server-side
 * before validation, so no field constraints here.
 */
public record ExamChoiceInput(String label, String text, boolean correct) {
}
