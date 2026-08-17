package com.center.parent.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.parent.entity.Parent;

public interface ParentRepository extends JpaRepository<Parent, UUID> {

    /** The parent profile behind a login account. */
    Optional<Parent> findByUserId(UUID userId);

    /** A parent by their Parent Code - the key for the parent forgot-password flow. */
    Optional<Parent> findBySerial(Integer serial);

    /** A parent account by its own number - used to name a WhatsApp recipient. */
    Optional<Parent> findFirstByPhone(String phone);

    /**
     * Parents approved-linked to at least one student in the given admin's
     * workspace, matching a name/phone query. Parent is not tenant-scoped, so the
     * link table pins the search to the admin's own students.
     */
    @Query("""
            select distinct p from Parent p, ParentStudentLink l
            where l.parentId = p.id
              and l.studentAdminId = :adminId
              and l.status = com.center.common.enums.LinkStatus.APPROVED
              and (lower(p.name) like lower(concat('%', :q, '%')) or p.phone like concat('%', :q, '%'))
            order by p.name
            """)
    List<Parent> searchForAdmin(@Param("adminId") UUID adminId, @Param("q") String q);
}
