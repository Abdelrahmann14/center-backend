package com.center.admin.repository;
import com.center.notification.entity.Notification;
import com.center.student.entity.Student;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // --- Students / Parents directory (cross-tenant) -----------------------

    interface StudentAdminRow {
        UUID getId();
        String getName();
        String getGrade();
        boolean getActive();
        Integer getSerial();
        String getGender();
        String getTeacher();
        String getPhones();
        String getParentPhones();
        /** The claimed login account; null = the student has not registered. */
        UUID getUserId();
        Instant getCreatedAt();
        String getCreatedBy();
        Instant getUpdatedAt();
        String getUpdatedBy();
    }

    /**
     * Every student across all workspaces, paginated. The free-text {@code q}
     * matches name, serial (prefix), or a student phone. Every other argument is
     * an optional equality filter (null = ignore). Nullable binds are wrapped in
     * CAST so Postgres can infer their type.
     */
    @Query(value = """
            SELECT s.id AS id, s.name AS name, s.grade AS grade, s.is_active AS active,
                   s.serial AS serial, s.gender AS gender, t.username AS teacher, s.user_id AS userId,
                   s.created_at AS createdAt, s.created_by AS createdBy,
                   s.updated_at AS updatedAt, s.updated_by AS updatedBy,
                   array_to_string(s.student_phones, ', ') AS phones,
                   array_to_string(s.parent_phones, ', ')  AS parentPhones
            FROM students s
            LEFT JOIN users t ON t.id = s.admin_id
            WHERE (CAST(:q AS text) IS NULL
                   OR (:q LIKE '0%' AND array_to_string(s.student_phones, ',') ILIKE '%' || :q || '%')
                   OR (:q ~ '^[1-9]' AND CAST(s.serial AS text) LIKE :q || '%')
                   OR (:q !~ '^[0-9]' AND s.name ILIKE '%' || :q || '%'))
              AND (CAST(:adminId AS uuid) IS NULL OR s.admin_id = CAST(:adminId AS uuid))
              AND (CAST(:grade AS text) IS NULL OR s.grade = CAST(:grade AS text))
              AND (CAST(:gender AS text) IS NULL OR s.gender = CAST(:gender AS text))
              AND (CAST(:registered AS boolean) IS NULL
                   OR (CAST(:registered AS boolean) = true AND s.user_id IS NOT NULL)
                   OR (CAST(:registered AS boolean) = false AND s.user_id IS NULL))
              AND (CAST(:active AS boolean) IS NULL OR s.is_active = CAST(:active AS boolean))
            ORDER BY s.name
            """,
            countQuery = """
            SELECT count(*) FROM students s
            WHERE (CAST(:q AS text) IS NULL
                   OR (:q LIKE '0%' AND array_to_string(s.student_phones, ',') ILIKE '%' || :q || '%')
                   OR (:q ~ '^[1-9]' AND CAST(s.serial AS text) LIKE :q || '%')
                   OR (:q !~ '^[0-9]' AND s.name ILIKE '%' || :q || '%'))
              AND (CAST(:adminId AS uuid) IS NULL OR s.admin_id = CAST(:adminId AS uuid))
              AND (CAST(:grade AS text) IS NULL OR s.grade = CAST(:grade AS text))
              AND (CAST(:gender AS text) IS NULL OR s.gender = CAST(:gender AS text))
              AND (CAST(:registered AS boolean) IS NULL
                   OR (CAST(:registered AS boolean) = true AND s.user_id IS NOT NULL)
                   OR (CAST(:registered AS boolean) = false AND s.user_id IS NULL))
              AND (CAST(:active AS boolean) IS NULL OR s.is_active = CAST(:active AS boolean))
            """,
            nativeQuery = true)
    Page<StudentAdminRow> findStudentSummaries(@Param("q") String q, @Param("adminId") String adminId,
            @Param("grade") String grade, @Param("gender") String gender,
            @Param("registered") Boolean registered, @Param("active") Boolean active, Pageable pageable);

    /** Distinct non-empty grades across every workspace, for the students filter. */
    @Query(value = "SELECT DISTINCT grade FROM students WHERE grade IS NOT NULL AND grade <> '' ORDER BY grade",
            nativeQuery = true)
    List<String> findDistinctGrades();

    // --- Notification recipient resolution (cross-tenant) ------------------
    // Each returns the user_ids of registered students matching a facet. Only
    // students carry religion/gender, and only registered ones (user_id set)
    // can receive an in-app notification.

    @Query(value = "SELECT user_id FROM students WHERE religion = :religion AND user_id IS NOT NULL",
            nativeQuery = true)
    List<UUID> findStudentUserIdsByReligion(@Param("religion") String religion);

    @Query(value = "SELECT user_id FROM students WHERE gender = :gender AND user_id IS NOT NULL",
            nativeQuery = true)
    List<UUID> findStudentUserIdsByGender(@Param("gender") String gender);

    @Query(value = "SELECT user_id FROM students WHERE grade = :grade AND user_id IS NOT NULL",
            nativeQuery = true)
    List<UUID> findStudentUserIdsByGrade(@Param("grade") String grade);

    /** Registered students belonging to one teacher's workspace. */
    @Query(value = "SELECT user_id FROM students WHERE admin_id = :adminId AND user_id IS NOT NULL",
            nativeQuery = true)
    List<UUID> findStudentUserIdsByTeacher(@Param("adminId") UUID adminId);

    interface PhoneRow {
        UUID getUserId();
        String getPhone();
    }

    /**
     * WhatsApp destinations for a set of recipients: a registered student's first
     * phone, or a parent's phone. Teachers have no phone and are simply absent.
     * {@code ids} is a comma-joined list of UUIDs.
     */
    @Query(value = """
            SELECT s.user_id AS userId, s.student_phones[1] AS phone
            FROM students s
            WHERE s.user_id = ANY (string_to_array(:ids, ',')::uuid[])
              AND s.student_phones[1] IS NOT NULL AND s.student_phones[1] <> ''
            UNION
            SELECT p.user_id AS userId, p.phone AS phone
            FROM parents p
            WHERE p.user_id = ANY (string_to_array(:ids, ',')::uuid[])
              AND p.phone IS NOT NULL AND p.phone <> ''
            """, nativeQuery = true)
    List<PhoneRow> findPhonesForUsers(@Param("ids") String ids);

    // Per-recipient variable data ({student.*}, {parent.*}, {teacher.name}).
    interface StudentVarRow {
        UUID getUserId();
        String getName();
        String getPhone();
        String getGrade();
        Integer getSerial();
        String getParentPhone();
    }

    interface SimpleUserRow {
        UUID getUserId();
        String getName();
        String getPhone();
    }

    @Query(value = """
            SELECT user_id AS userId, name AS name, student_phones[1] AS phone,
                   grade AS grade, serial AS serial, parent_phones[1] AS parentPhone
            FROM students WHERE user_id = ANY (string_to_array(:ids, ',')::uuid[])
            """, nativeQuery = true)
    List<StudentVarRow> findStudentVars(@Param("ids") String ids);

    @Query(value = """
            SELECT user_id AS userId, name AS name, phone AS phone
            FROM parents WHERE user_id = ANY (string_to_array(:ids, ',')::uuid[])
            """, nativeQuery = true)
    List<SimpleUserRow> findParentVars(@Param("ids") String ids);

    @Query(value = """
            SELECT id AS userId, username AS name, CAST(NULL AS text) AS phone
            FROM users WHERE role = 'admin' AND id = ANY (string_to_array(:ids, ',')::uuid[])
            """, nativeQuery = true)
    List<SimpleUserRow> findTeacherVars(@Param("ids") String ids);

    interface ParentAdminRow {
        UUID getId();
        String getName();
        String getPhone();
        Integer getSerial();
        boolean getActive();
        long getStudentCount();
        UUID getUserId();
        Instant getCreatedAt();
        String getCreatedBy();
        Instant getUpdatedAt();
        String getUpdatedBy();
    }

    /** Every parent across all workspaces, name-filtered, paginated. */
    @Query(value = """
            SELECT p.id AS id, p.name AS name, p.phone AS phone, p.serial AS serial,
                   u.is_active AS active, p.user_id AS userId,
                   p.created_at AS createdAt, p.created_by AS createdBy,
                   p.updated_at AS updatedAt, p.updated_by AS updatedBy,
                   (SELECT count(*) FROM parent_student_links l WHERE l.parent_id = p.id) AS studentCount
            FROM parents p
            LEFT JOIN users u ON u.id = p.user_id
            WHERE (:q IS NULL OR p.name ILIKE '%' || :q || '%')
            ORDER BY p.name
            """,
            countQuery = """
            SELECT count(*) FROM parents p
            WHERE (:q IS NULL OR p.name ILIKE '%' || :q || '%')
            """,
            nativeQuery = true)
    Page<ParentAdminRow> findParentSummaries(@Param("q") String q, Pageable pageable);

    // --- Detail reads ------------------------------------------------------

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

    interface StudentDetailRow {
        UUID getId();
        String getName();
        String getGrade();
        Integer getSerial();
        boolean getActive();
        String getTeacher();
        String getPhones();
        String getParentPhones();
        UUID getUserId();
        byte[] getPhotoData();
        String getPhotoType();
        String getGender();
        String getReligion();
        String getAcademicTrack();
        String getSchool();
        String getCity();
        LocalDate getBirthDate();
        BigDecimal getLessonPrice();
        boolean getDiscounted();
        String getNotes();
    }

    @Query(value = """
            SELECT s.id AS id, s.name AS name, s.grade AS grade, s.serial AS serial,
                   s.is_active AS active, t.username AS teacher, s.user_id AS userId,
                   array_to_string(s.student_phones, ', ') AS phones,
                   array_to_string(s.parent_phones, ', ')  AS parentPhones,
                   u.photo_data AS photoData, u.photo_type AS photoType,
                   s.gender AS gender, s.religion AS religion, s.academic_track AS academicTrack,
                   s.school AS school, s.city AS city, s.birth_date AS birthDate,
                   s.lesson_price AS lessonPrice, s.is_discounted AS discounted, s.notes AS notes
            FROM students s
            LEFT JOIN users t ON t.id = s.admin_id
            LEFT JOIN users u ON u.id = s.user_id
            WHERE s.id = :studentId
            """, nativeQuery = true)
    StudentDetailRow findStudentDetail(@Param("studentId") UUID studentId);

    interface NameRow {
        String getName();
        String getDetail();
    }

    /** Parents linked to a student (name + phone), for the student detail page. */
    @Query(value = """
            SELECT p.name AS name, p.phone AS detail
            FROM parent_student_links l
            JOIN parents p ON p.id = l.parent_id
            WHERE l.student_id = :studentId
            ORDER BY p.name
            """, nativeQuery = true)
    List<NameRow> findStudentParents(@Param("studentId") UUID studentId);

    interface ParentDetailRow {
        UUID getId();
        String getName();
        String getPhone();
        Integer getSerial();
        boolean getActive();
        UUID getUserId();
        byte[] getPhotoData();
        String getPhotoType();
    }

    @Query(value = """
            SELECT p.id AS id, p.name AS name, p.phone AS phone, p.serial AS serial,
                   u.is_active AS active, p.user_id AS userId,
                   u.photo_data AS photoData, u.photo_type AS photoType
            FROM parents p
            LEFT JOIN users u ON u.id = p.user_id
            WHERE p.id = :parentId
            """, nativeQuery = true)
    ParentDetailRow findParentDetail(@Param("parentId") UUID parentId);

    /** Students linked to a parent (name + owning teacher), for the parent detail page. */
    @Query(value = """
            SELECT s.name AS name, t.username AS detail
            FROM parent_student_links l
            JOIN students s ON s.id = l.student_id
            LEFT JOIN users t ON t.id = s.admin_id
            WHERE l.parent_id = :parentId
            ORDER BY s.name
            """, nativeQuery = true)
    List<NameRow> findParentStudents(@Param("parentId") UUID parentId);

    /** Toggle a student's active flag (cross-tenant; the super admin is unbound). */
    @Modifying
    @Query(value = "UPDATE students SET is_active = :active WHERE id = :studentId", nativeQuery = true)
    void updateStudentActive(@Param("studentId") UUID studentId, @Param("active") boolean active);

    // --- Student / parent edit + delete (cross-tenant) ---------------------

    @Modifying
    @Query(value = """
            UPDATE students SET
              name = :name,
              grade = :grade,
              gender = :gender,
              religion = :religion,
              academic_track = :academicTrack,
              school = :school,
              city = :city,
              birth_date = :birthDate,
              lesson_price = :lessonPrice,
              is_discounted = :discounted,
              notes = :notes,
              student_phones = string_to_array(:phones, ','),
              parent_phones  = string_to_array(:parentPhones, ',')
            WHERE id = :studentId
            """, nativeQuery = true)
    void updateStudent(@Param("studentId") UUID studentId, @Param("name") String name,
            @Param("grade") String grade, @Param("gender") String gender,
            @Param("religion") String religion, @Param("academicTrack") String academicTrack,
            @Param("school") String school, @Param("city") String city,
            @Param("birthDate") LocalDate birthDate, @Param("lessonPrice") BigDecimal lessonPrice,
            @Param("discounted") boolean discounted, @Param("notes") String notes,
            @Param("phones") String phones, @Param("parentPhones") String parentPhones);

    // A student's children removed first, then the row (exam_attempts cascade off it).
    @Modifying
    @Query(value = "DELETE FROM registrations WHERE student_id = :studentId", nativeQuery = true)
    void deleteStudentRegistrations(@Param("studentId") UUID studentId);

    @Modifying
    @Query(value = "DELETE FROM attendance WHERE student_id = :studentId", nativeQuery = true)
    void deleteStudentAttendance(@Param("studentId") UUID studentId);

    @Modifying
    @Query(value = "DELETE FROM parent_student_links WHERE student_id = :studentId", nativeQuery = true)
    void deleteStudentLinks(@Param("studentId") UUID studentId);

    @Modifying
    @Query(value = "DELETE FROM students WHERE id = :studentId", nativeQuery = true)
    void deleteStudentRow(@Param("studentId") UUID studentId);

    @Modifying
    @Query(value = "UPDATE parents SET name = :name, phone = :phone WHERE id = :parentId", nativeQuery = true)
    void updateParent(@Param("parentId") UUID parentId, @Param("name") String name, @Param("phone") String phone);

    @Modifying
    @Query(value = "DELETE FROM parent_student_links WHERE parent_id = :parentId", nativeQuery = true)
    void deleteParentLinks(@Param("parentId") UUID parentId);

    @Modifying
    @Query(value = "DELETE FROM parents WHERE id = :parentId", nativeQuery = true)
    void deleteParentRow(@Param("parentId") UUID parentId);

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
