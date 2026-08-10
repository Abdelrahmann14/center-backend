package com.center.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.Religion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Super admin editing a student's full profile (cross-tenant). */
public record SuperStudentUpdateRequest(
        @NotBlank(message = "مطلوب") @Size(max = 120) String name,
        String grade,
        Gender gender,
        Religion religion,
        AcademicTrack academicTrack,
        String school,
        String city,
        LocalDate birthDate,
        BigDecimal lessonPrice,
        boolean discounted,
        String notes,
        List<String> studentPhones,
        List<String> parentPhones) {

    public String studentPhonesCsv() {
        return studentPhones == null ? "" : String.join(",", studentPhones);
    }

    public String parentPhonesCsv() {
        return parentPhones == null ? "" : String.join(",", parentPhones);
    }

    /** Enums are stored as their Arabic display value (see {@link Gender}). */
    public String genderValue() {
        return gender == null ? null : gender.getValue();
    }

    public String religionValue() {
        return religion == null ? null : religion.getValue();
    }

    public String academicTrackValue() {
        return academicTrack == null ? null : academicTrack.getValue();
    }
}
