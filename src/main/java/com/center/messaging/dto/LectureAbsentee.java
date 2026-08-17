package com.center.messaging.dto;

import java.util.List;
import java.util.UUID;

/**
 * One student of a group who did not sit a lesson - in ANY group, so a student
 * who took it with another group is not an absentee - and whether the absence
 * message has already reached them.
 */
public record LectureAbsentee(UUID studentId, Integer serial, String name,
        List<String> parentPhones, boolean sent) {
}
