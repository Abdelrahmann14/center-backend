package com.center.student.dto;

import java.util.UUID;

/** A selectable teacher (Admin) for the student registration picker. */
public record TeacherOptionResponse(UUID id, String name, String photo) {
}
