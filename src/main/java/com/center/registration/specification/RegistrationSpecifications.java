package com.center.registration.specification;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.center.registration.dto.RegistrationFilter;
import com.center.registration.entity.Registration;
import com.center.common.enums.RegistrationStatus;

public final class RegistrationSpecifications {

    private RegistrationSpecifications() {
    }

    public static Specification<Registration> matching(RegistrationFilter filter) {
        return Specification.allOf(
                lecture(filter.lectureId()),
                filter.isGroupless() ? groupless() : group(filter.groupId()),
                status(filter.status()),
                studentNameContains(filter.search()));
    }

    private static Specification<Registration> lecture(UUID lectureId) {
        return lectureId == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("lecture").get("id"), lectureId);
    }

    private static Specification<Registration> group(UUID groupId) {
        return groupId == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("group").get("id"), groupId);
    }

    /** Rows registered under no group at all. */
    private static Specification<Registration> groupless() {
        return (root, query, cb) -> cb.isNull(root.get("group"));
    }

    private static Specification<Registration> status(RegistrationStatus status) {
        return status == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Registration> studentNameContains(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.strip().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("student").get("name")), pattern);
    }
}
