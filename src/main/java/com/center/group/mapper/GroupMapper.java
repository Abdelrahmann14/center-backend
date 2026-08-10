package com.center.group.mapper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.center.group.dto.GroupResponse;
import com.center.group.entity.Group;

@Mapper
public interface GroupMapper {

    DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "startTime", source = "startTime", qualifiedByName = "formatTime")
    GroupResponse toResponse(Group group);

    List<GroupResponse> toResponses(List<Group> groups);

    /** The UI renders and submits times as "HH:mm", never with seconds. */
    @Named("formatTime")
    static String formatTime(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }
}
