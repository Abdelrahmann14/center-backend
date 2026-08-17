package com.center.student.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.center.common.constants.ValidationRules;
import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.Religion;
import com.center.common.validation.ArabicName;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record StudentRequest(
        @NotBlank(message = "مطلوب")
        @Size(max = ValidationRules.STUDENT_NAME_MAX)
        @ArabicName String name,

        @NotBlank(message = "مطلوب") @Size(max = ValidationRules.GRADE_NAME_MAX) String grade,
        String school,
        String city,
        Gender gender,
        UUID groupId,

        @NotEmpty(message = "أدخل رقم هاتف واحد الطالب على الأقل")
        List<String> studentPhones,

        @NotEmpty(message = "أدخل رقم هاتف واحد ولي الأمر على الأقل")
        List<String> parentPhones,

        Religion religion,
        AcademicTrack academicTrack,
        @PositiveOrZero BigDecimal lessonPrice,

        /** Required (min length enforced in the service) only when discounted. */
        String discountReason,
        String notes,

        /** False = blocked. Absent means active, so older clients keep working. */
        @JsonProperty("is_active") Boolean isActive,

        /** Why the student is blocked; ignored while they are active. */
        @Size(max = 500) String blockReason,

        /** Lets the user knowingly share a phone number between siblings. */
        Boolean allowDuplicatePhone) {

    public boolean allowsDuplicatePhone() {
        return Boolean.TRUE.equals(allowDuplicatePhone);
    }

    public boolean activeOrDefault() {
        return !Boolean.FALSE.equals(isActive);
    }
}
