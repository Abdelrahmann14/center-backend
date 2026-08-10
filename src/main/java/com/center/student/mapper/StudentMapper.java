package com.center.student.mapper;

import java.util.Arrays;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.center.student.dto.StudentResponse;
import com.center.student.entity.Student;

@Mapper
public interface StudentMapper {

    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "isDiscounted", source = "discounted")
    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "registered", expression = "java(student.getUserId() != null)")
    // The Google-sync flag needs a repository lookup, so the list path fills it in
    // afterwards via StudentResponse#withGoogleSynced; here it defaults to false.
    @Mapping(target = "googleSynced", ignore = true)
    StudentResponse toResponse(Student student);

    List<StudentResponse> toResponses(List<Student> students);

    default List<String> toPhoneList(String[] phones) {
        return phones == null ? List.of() : Arrays.asList(phones);
    }
}
