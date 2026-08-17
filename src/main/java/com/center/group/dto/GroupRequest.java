package com.center.group.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.center.common.constants.ValidationRules;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GroupRequest(
        @NotNull(message = "مطلوب") @Min(0) @Max(6) Integer dayOfWeek,

        @NotBlank(message = "مطلوب")
        @Pattern(regexp = ValidationRules.TIME_PATTERN, message = "صيغة الوقت غير صحيحة")
        String startTime,

        @NotBlank(message = "مطلوب") @Size(max = ValidationRules.CENTER_NAME_MAX) String centerName,
        @NotBlank(message = "مطلوب") @Size(max = ValidationRules.GRADE_NAME_MAX) String grade,

        /**
         * Active flag. Online this has its own PATCH endpoint, so the form never
         * sends it and null has to mean "leave it as it is". An offline client
         * has no second call to make - the sync contract carries whole rows, not
         * field patches - so it sends the flag here.
         */
        @JsonProperty("is_active") Boolean isActive) {
}
