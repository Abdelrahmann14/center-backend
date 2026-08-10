package com.center.parent.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.parent.entity.ParentStudentLink;
import com.center.common.enums.LinkStatus;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, UUID> {

    /** How many parents a student already has in a given state (limit is 2 approved). */
    long countByStudentIdAndStatus(UUID studentId, LinkStatus status);

    /** How many links a parent holds in a given state (0 approved => first approval). */
    long countByParentIdAndStatus(UUID parentId, LinkStatus status);

    /** Guards against a duplicate request for the same pair, in any state. */
    boolean existsByParentIdAndStudentId(UUID parentId, UUID studentId);

    /** A student's requests in one state - newest first (pending = their inbox). */
    List<ParentStudentLink> findByStudentIdAndStatusOrderByCreatedAtDesc(UUID studentId, LinkStatus status);

    /** A parent's links in one state (approved = their children). */
    List<ParentStudentLink> findByParentIdAndStatus(UUID parentId, LinkStatus status);

    /** A student's links in one state (approved = their guardians). */
    List<ParentStudentLink> findByStudentIdAndStatus(UUID studentId, LinkStatus status);

    Optional<ParentStudentLink> findByParentIdAndStudentId(UUID parentId, UUID studentId);
}
