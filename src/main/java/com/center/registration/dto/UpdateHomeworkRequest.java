package com.center.registration.dto;

import com.center.common.enums.HomeworkFlag;

/** Null clears the flag, meaning the homework had no issue. */
public record UpdateHomeworkRequest(HomeworkFlag homeworkFlag) {
}
