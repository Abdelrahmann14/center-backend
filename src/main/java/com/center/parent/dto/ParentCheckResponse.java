package com.center.parent.dto;
import com.center.student.entity.Student;

/**
 * Result of checking a Student Code before parent registration.
 *
 * @param studentName the child's name, to confirm the parent picked the right code
 * @param canLink     false when the student already has the maximum of two parents
 */
public record ParentCheckResponse(String studentName, boolean canLink) {
}
