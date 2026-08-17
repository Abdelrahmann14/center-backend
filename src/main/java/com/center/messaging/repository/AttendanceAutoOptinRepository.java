package com.center.messaging.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.messaging.entity.AttendanceAutoOptin;

public interface AttendanceAutoOptinRepository extends JpaRepository<AttendanceAutoOptin, UUID> {

    /** The auto-send switch for one (lecture, group), scoped by @TenantId. */
    Optional<AttendanceAutoOptin> findByLectureIdAndGroupId(UUID lectureId, UUID groupId);
}
