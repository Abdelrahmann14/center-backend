package com.center.google.dto;

import java.util.UUID;

/** One grade with the admin's three optional Google-contact marks. */
public record GoogleMarkResponse(
        UUID gradeId,
        String gradeName,
        boolean gradeActive,
        String studentMark,
        String parentMark,
        String bothMark) {
}
