package com.center.admin.dto;
import com.center.parent.dto.LinkedPersonResponse;

import java.util.List;
import java.util.UUID;

/** Full parent profile for the super admin's parent detail page. */
public record ParentDetailResponse(
        UUID id,
        String name,
        String phone,
        Integer serial,
        boolean active,
        UUID userId,
        String photo,
        List<LinkedPersonResponse> students) {
}
