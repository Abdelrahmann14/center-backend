package com.center.student.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.group.entity.Group;
import com.center.student.entity.Student;

public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    /** Stands in for "no student to exclude" - never a real id. */
    UUID NO_EXCLUSION = UUID.fromString("00000000-0000-0000-0000-000000000000");

    long countByActiveTrue();

    /** How many students (active or not) are assigned to a group - the delete guard. */
    long countByGroup_Id(UUID groupId);

    /**
     * Move every student of one group to another, in one statement. Used when a
     * group is deleted: no student may be left group-less, so they are transferred
     * to the chosen target first. @TenantId keeps this within the workspace.
     */
    @Modifying
    @Query("UPDATE Student s SET s.group = :target WHERE s.group.id = :sourceId")
    int reassignGroup(@Param("sourceId") UUID sourceId, @Param("target") Group target);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Active students assigned to a group - the audience of a scheduled exam. */
    List<Student> findByGroup_IdAndActiveTrue(UUID groupId);

    // JPQL: Hibernate's @TenantId scopes these to the current workspace, so the
    // form only ever suggests this admin's own schools/cities.
    @Query("SELECT DISTINCT s.school FROM Student s WHERE s.school IS NOT NULL ORDER BY s.school")
    List<String> findDistinctSchools();

    @Query("SELECT DISTINCT s.city FROM Student s WHERE s.city IS NOT NULL ORDER BY s.city")
    List<String> findDistinctCities();

    /**
     * The highest student code issued IN ONE WORKSPACE.
     *
     * <p>It used to read across all of them, because the code came from a single
     * global sequence. V53 scoped codes to the workspace, so reading globally now
     * previews a number from someone else's roster - a brand new workspace would
     * be told its first student is number 4,823.
     *
     * <p>Native SQL because {@code @TenantId} does not reach a native query; the
     * workspace is therefore passed in and filtered explicitly, matching what the
     * assign_student_serial trigger computes on insert.
     */
    @Query(value = "SELECT coalesce(max(serial), 0) FROM students WHERE admin_id = :adminId",
            nativeQuery = true)
    int findMaxSerial(UUID adminId);

    /**
     * Every distinct number on this workspace's roster, students and guardians
     * together.
     *
     * <p>Read by the WhatsApp number check, which is asked "which of these do we
     * not know about yet". The browser holds these numbers too, but shipping
     * hundreds of them up to be told which are unknown is a payload for nothing -
     * the question is entirely answerable server-side.
     */
    @Query(value = """
            SELECT DISTINCT btrim(p)
            FROM students s, unnest(s.student_phones || s.parent_phones) p
            WHERE s.admin_id = :adminId AND btrim(p) <> ''
            """, nativeQuery = true)
    List<String> allPhones(@Param("adminId") UUID adminId);

    // ---- barcode card ------------------------------------------------------
    //
    // "Everyone who never got their card" is the resend button's whole audience,
    // and it is deliberately narrower than "barcode_sent_at is null": a blocked
    // student is not being taught, and a student with no number of their own has
    // nowhere to receive it. Including either would make the button report work
    // it can never finish, and would write a failed log row per student per
    // press. Native SQL because the phone test needs unnest(); the workspace is
    // therefore filtered explicitly, since @TenantId does not reach a native
    // query.

    String PENDING_BARCODE_WHERE = """
            WHERE admin_id = :adminId
              AND is_active
              AND barcode_sent_at IS NULL
              AND EXISTS (SELECT 1 FROM unnest(student_phones) p WHERE btrim(p) <> '')
            """;

    @Query(value = "SELECT count(*) FROM students " + PENDING_BARCODE_WHERE, nativeQuery = true)
    long countPendingBarcode(@Param("adminId") UUID adminId);

    /** The next batch to send to, oldest student code first. */
    @Query(value = "SELECT id FROM students " + PENDING_BARCODE_WHERE
            + " ORDER BY serial LIMIT :limit", nativeQuery = true)
    List<UUID> findPendingBarcodeIds(@Param("adminId") UUID adminId, @Param("limit") int limit);

    /**
     * Record that the card reached this student, keeping the FIRST instant: the
     * {@code IS NULL} guard is what makes a later manual resend leave the
     * original date alone. @TenantId scopes it to the workspace.
     */
    @Modifying
    @Query("UPDATE Student s SET s.barcodeSentAt = :at WHERE s.id = :id AND s.barcodeSentAt IS NULL")
    int markBarcodeSent(@Param("id") UUID id, @Param("at") java.time.OffsetDateTime at);

    /** Just enough to locate a student and the workspace that owns them. */
    interface StudentIdentity {
        UUID getId();

        UUID getAdminId();
    }

    /** A roster row reduced to what the Google reconciler actually reads. */
    interface RosterRow {
        UUID getId();

        java.time.OffsetDateTime getUpdatedAt();
    }

    /**
     * Every student in one workspace as (id, updatedAt) only.
     *
     * <p>Native and explicitly scoped, so it bypasses the {@code @TenantId}
     * filter and can be called from the scheduler with no tenant bound. The
     * point is what it does NOT do: the reconciler used to call
     * {@code findAll()} and hydrate the entire roster as managed entities -
     * every column, every association, on a ten-minute timer, per workspace -
     * to read two fields off each one. Two columns is a fixed, small cost that
     * does not grow with how wide the student record becomes.
     */
    @Query(value = "SELECT id AS id, updated_at AS updatedAt FROM students WHERE admin_id = :adminId",
            nativeQuery = true)
    List<RosterRow> findRoster(@Param("adminId") UUID adminId);

    /**
     * True when ANY workspace already holds a student with this name. Student
     * names double as login usernames, which are globally unique - so this check
     * must see past the tenant filter too.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM students WHERE lower(name) = lower(:name))",
            nativeQuery = true)
    boolean nameExistsAnywhere(@Param("name") String name);

    /**
     * True when any OTHER student already owns one of these phone numbers.
     *
     * <p>Native: this is Postgres' array-overlap operator, which JPQL has no
     * equivalent for. Phones arrive comma-joined because they are digits-only,
     * so a comma can never appear inside one.
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM students
              WHERE student_phones && string_to_array(:phones, ',')
                AND id <> :excludeId
                AND admin_id = :adminId
            )
            """, nativeQuery = true)
    boolean phoneTaken(@Param("phones") String phones, @Param("excludeId") UUID excludeId,
            @Param("adminId") UUID adminId);

    interface PhoneOwner {
        String getName();

        /** The owner's phones, comma-joined. */
        String getPhones();
    }

    /**
     * The students already using any of these numbers, so the form can name the
     * clash.
     *
     * <p>Native for the same reason as {@link #phoneTaken}: array overlap.
     */
    @Query(value = """
            SELECT name AS name, array_to_string(student_phones, ',') AS phones
            FROM students
            WHERE student_phones && string_to_array(:phones, ',')
              AND id <> :excludeId
              AND admin_id = :adminId
            """, nativeQuery = true)
    List<PhoneOwner> findPhoneOwners(@Param("phones") String phones, @Param("excludeId") UUID excludeId,
            @Param("adminId") UUID adminId);

    /** Who a number belongs to, and in what capacity. */
    interface PhoneMatch {
        UUID getId();

        String getName();

        Integer getSerial();

        /** STUDENT when it is the student's own number, PARENT when the guardian's. */
        String getRole();
    }

    /**
     * The student a WhatsApp number belongs to, so a send that carried only a
     * phone can still be logged against a person.
     *
     * <p>Native because the phones are {@code text[]} and this needs the array
     * containment operator; the workspace is passed explicitly for the same
     * reason - a native query is outside Hibernate's {@code @TenantId} scoping.
     * A number held by both the student and the guardian resolves as STUDENT,
     * which is the narrower, more informative answer.
     */
    @Query(value = """
            SELECT id AS id, name AS name, serial AS serial,
                   CASE WHEN :phone = ANY(student_phones) THEN 'STUDENT' ELSE 'PARENT' END AS role
            FROM students
            WHERE admin_id = :adminId
              AND (:phone = ANY(student_phones) OR :phone = ANY(parent_phones))
            ORDER BY CASE WHEN :phone = ANY(student_phones) THEN 0 ELSE 1 END
            LIMIT 1
            """, nativeQuery = true)
    Optional<PhoneMatch> findByAnyPhone(@Param("adminId") UUID adminId, @Param("phone") String phone);
}
