package com.center.registration.dto;

import java.util.UUID;

import com.center.common.enums.HomeworkFlag;
import com.center.common.enums.RegistrationStatus;

import jakarta.validation.constraints.NotNull;

public record CreateRegistrationRequest(
        @NotNull(message = "مطلوب") UUID lectureId,
        @NotNull(message = "مطلوب") UUID studentId,
        UUID groupId,
        RegistrationStatus status,
        HomeworkFlag homeworkFlag) {

    public RegistrationStatus statusOrDefault() {
        return status == null ? RegistrationStatus.PRESENT : status;
    }
}
