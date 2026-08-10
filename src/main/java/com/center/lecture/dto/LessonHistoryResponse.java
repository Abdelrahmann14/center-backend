package com.center.lecture.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.center.common.enums.HomeworkFlag;
import com.center.common.enums.RegistrationStatus;

/**
 * One lesson in a student's grade history.
 *
 * @param id           the LECTURE id - unique per card whether attended or not
 * @param examGrade    the lesson's maximum mark, free text ("50", "من 50")
 * @param homeworkFlag null when the homework had no issue
 */
public record LessonHistoryResponse(
        UUID id,
        String lectureName,
        RegistrationStatus status,
        BigDecimal examScore,
        String examGrade,
        HomeworkFlag homeworkFlag) {
}
