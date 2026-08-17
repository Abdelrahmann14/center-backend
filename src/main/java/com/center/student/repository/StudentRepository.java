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

    /** The student record for a claimed login account, within the current tenant. */
    Optional<Student> findByUserId(UUID userId);

    /** Active students assigned to a group - the audience of a scheduled exam. */
    List<Student> findByGroup_IdAndActiveTrue(UUID groupId);

    /**
     * Active students assigned to a group who did not attend ANY lesson within the
     * given week - the audience of the weekly absence message. Both Student and
     * Attendance are @TenantId, so under a bound tenant this stays scoped to the
     * one workspace.
     */
    @Query("""
            SELECT s FROM Student s
            WHERE s.active = true AND s.group IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM Attendance a
                WHERE a.studentId = s.id AND a.attendedOn BETWEEN :start AND :end
              )
            """)
    List<Student> findWeeklyAbsentees(@Param("start") java.time.LocalDate start,
            @Param("end") java.time.LocalDate end);

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

    /** Just enough to locate a student and the workspace that owns them. */
    interface StudentIdentity {
        UUID getId();

        UUID getAdminId();
    }

    /**
     * Find a student by their code, across every workspace.
     *
     * <p>Native on purpose: public self-registration runs with no tenant bound,
     * so the @TenantId filter would hide every row. The owning workspace is
     * returned so the caller can continue scoped to it.
     */
    @Query(value = "SELECT id AS id, admin_id AS adminId FROM students WHERE serial = :serial",
            nativeQuery = true)
    Optional<StudentIdentity> findIdentityBySerial(@Param("serial") int serial);

    /** The owning workspace of a student by id, ignoring the tenant filter. */
    @Query(value = "SELECT admin_id FROM students WHERE id = :id", nativeQuery = true)
    Optional<UUID> findAdminIdById(@Param("id") UUID id);

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
     * True when ANY workspace already registered this number as a STUDENT phone.
     * A student's own number identifies them, so it must be unique platform-wide
     * - parent numbers are deliberately not checked, since siblings share one.
     *
     * <p>Native: array containment, and self-registration runs with no tenant
     * bound, so the check must see past the discriminator filter.
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM students WHERE student_phones @> ARRAY[cast(:phone AS text)]
            )
            """, nativeQuery = true)
    boolean studentPhoneExistsAnywhere(@Param("phone") String phone);

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
