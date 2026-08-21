package com.center.admin.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.user.entity.User;

/**
 * Cross-tenant queries reserved for the super admin. These use native SQL on
 * purpose: Hibernate's {@code @TenantId} filter would otherwise scope them to a
 * single workspace, but the super admin manages every Admin at once.
 *
 * <p>The entity type is only nominal - every method here is native and touches
 * whichever tables it names.
 */
public interface SuperAdminRepository extends JpaRepository<User, UUID> {

    interface AdminSummaryRow {
        UUID getId();

        String getUsername();

        String getEmail();

        boolean getActive();

        /** timestamptz arrives as an Instant in native-query projections. */
        /** WhatsApp number invoices are sent to; null until it is set. */
        String getPhone();

        /** Public contact number message templates print; null until it is set. */
        String getOfficePhone();

        Instant getCreatedAt();

        /** Username recorded by the auditing listener; null on pre-audit rows. */
        String getCreatedBy();

        Instant getUpdatedAt();

        String getUpdatedBy();

        long getStudentCount();

        long getAssistantCount();

        byte[] getPhotoData();

        String getPhotoType();
    }

    /**
     * Every Admin with per-workspace counts, for the super admin's list view.
     * Optional case-insensitive name filter ({@code q} null/blank = all).
     */
    @Query(value = """
            SELECT u.id         AS id,
                   u.username   AS username,
                   u.email      AS email,
                   u.phone      AS phone,
                   u.office_phone AS officePhone,
                   u.is_active  AS active,
                   u.created_at AS createdAt,
                   u.created_by AS createdBy,
                   u.updated_at AS updatedAt,
                   u.updated_by AS updatedBy,
                   u.photo_data AS photoData,
                   u.photo_type AS photoType,
                   (SELECT count(*) FROM students s WHERE s.admin_id = u.id) AS studentCount,
                   (SELECT count(*) FROM users a
                     WHERE a.admin_id = u.id AND a.role = 'user')            AS assistantCount
            FROM users u
            WHERE u.role = 'admin'
              AND (:q IS NULL OR u.username ILIKE '%' || :q || '%')
            ORDER BY u.created_at
            """, nativeQuery = true)
    List<AdminSummaryRow> findAdminSummaries(@Param("q") String q);

    /** One Admin's counts, or null when the id is not an admin. */
    @Query(value = """
            SELECT u.id         AS id,
                   u.username   AS username,
                   u.email      AS email,
                   u.phone      AS phone,
                   u.office_phone AS officePhone,
                   u.is_active  AS active,
                   u.created_at AS createdAt,
                   u.created_by AS createdBy,
                   u.updated_at AS updatedAt,
                   u.updated_by AS updatedBy,
                   u.photo_data AS photoData,
                   u.photo_type AS photoType,
                   (SELECT count(*) FROM students s WHERE s.admin_id = u.id) AS studentCount,
                   (SELECT count(*) FROM users a
                     WHERE a.admin_id = u.id AND a.role = 'user')            AS assistantCount
            FROM users u
            WHERE u.role = 'admin' AND u.id = :adminId
            """, nativeQuery = true)
    AdminSummaryRow findAdminSummary(@Param("adminId") UUID adminId);

    interface AssistantAdminRow {
        UUID getId();
        String getUsername();
        String getEmail();
        boolean getActive();
        byte[] getPhotoData();
        String getPhotoType();
    }

    /** One teacher's assistants (their login accounts), for the teacher detail page. */
    @Query(value = """
            SELECT a.id AS id, a.username AS username, a.email AS email, a.is_active AS active,
                   a.photo_data AS photoData, a.photo_type AS photoType
            FROM users a
            WHERE a.role = 'user' AND a.admin_id = :adminId
            ORDER BY a.username
            """, nativeQuery = true)
    List<AssistantAdminRow> findAssistants(@Param("adminId") UUID adminId);

    // --- Workspace hard-delete ---------------------------------------------
    // Rows are removed child-before-parent, all scoped by admin_id, so no
    // foreign key is ever violated. Called in order inside one transaction.

    @Modifying
    @Query(value = "DELETE FROM registrations WHERE admin_id = :adminId", nativeQuery = true)
    void deleteRegistrations(@Param("adminId") UUID adminId);

    @Modifying
    @Query(value = "DELETE FROM attendance WHERE admin_id = :adminId", nativeQuery = true)
    void deleteAttendance(@Param("adminId") UUID adminId);

    @Modifying
    @Query(value = "DELETE FROM center_grades WHERE admin_id = :adminId", nativeQuery = true)
    void deleteCenterGrades(@Param("adminId") UUID adminId);

    @Modifying
    @Query(value = "DELETE FROM students WHERE admin_id = :adminId", nativeQuery = true)
    void deleteStudents(@Param("adminId") UUID adminId);

    @Modifying
    @Query(value = "DELETE FROM groups WHERE admin_id = :adminId", nativeQuery = true)
    void deleteGroups(@Param("adminId") UUID adminId);

    @Modifying
    @Query(value = "DELETE FROM centers WHERE admin_id = :adminId", nativeQuery = true)
    void deleteCenters(@Param("adminId") UUID adminId);

    @Modifying
    @Query(value = "DELETE FROM lectures WHERE admin_id = :adminId", nativeQuery = true)
    void deleteLectures(@Param("adminId") UUID adminId);

    /** The workspace's assistants; their work_sessions cascade away. */
    @Modifying
    @Query(value = "DELETE FROM users WHERE admin_id = :adminId", nativeQuery = true)
    void deleteAssistants(@Param("adminId") UUID adminId);
}
