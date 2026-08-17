package com.center.registration.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.center.common.enums.HomeworkFlag;
import com.center.common.enums.RegistrationStatus;

import jakarta.validation.constraints.NotNull;

public record CreateRegistrationRequest(
        @NotNull(message = "مطلوب") UUID lectureId,
        @NotNull(message = "مطلوب") UUID studentId,
        UUID groupId,
        RegistrationStatus status,
        HomeworkFlag homeworkFlag,

        /**
         * When the student was actually marked present. Only an offline replay
         * sends it: it is the device's own clock reading from the moment the
         * attendance was taken, which the server cannot reconstruct afterwards.
         * Absent - every online call - means "now".
         */
        OffsetDateTime attendedAt) {

    public RegistrationStatus statusOrDefault() {
        return status == null ? RegistrationStatus.PRESENT : status;
    }

    /**
     * The attendance instant to record. A device clock claiming the future is not
     * trusted - a badly set phone must not stamp tomorrow onto today's lesson.
     */
    public OffsetDateTime attendedAtOrNow() {
        OffsetDateTime now = OffsetDateTime.now();
        return attendedAt == null || attendedAt.isAfter(now) ? now : attendedAt;
    }
}
