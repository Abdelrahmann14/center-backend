package com.center.registration.mapper;

import java.util.Arrays;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.center.registration.dto.RegistrationResponse;
import com.center.registration.entity.Registration;

/** Flattens a registration and its student into the row the lesson table renders. */
@Mapper
public interface RegistrationMapper {

    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "serial", source = "student.serial")
    @Mapping(target = "name", source = "student.name")
    @Mapping(target = "grade", source = "student.grade")
    @Mapping(target = "gender", source = "student.gender")
    @Mapping(target = "school", source = "student.school")
    @Mapping(target = "city", source = "student.city")
    @Mapping(target = "religion", source = "student.religion")
    @Mapping(target = "academicTrack", source = "student.academicTrack")
    @Mapping(target = "lessonPrice", source = "student.lessonPrice")
    @Mapping(target = "studentPhones", source = "student.studentPhones")
    @Mapping(target = "parentPhones", source = "student.parentPhones")
    @Mapping(target = "isActive", source = "student.active")
    // attendedAt maps by name from the entity's own column - the instant the
    // student was marked present, which for an offline attendance is NOT
    // createdAt (that is when the queued row finally reached the database).
    @Mapping(target = "assignedGroupId", source = "student.group.id")
    @Mapping(target = "registeredGroupId", source = "group.id")
    RegistrationResponse toResponse(Registration registration);

    List<RegistrationResponse> toResponses(List<Registration> registrations);

    default List<String> toPhoneList(String[] phones) {
        return phones == null ? List.of() : Arrays.asList(phones);
    }
}
