package com.center.student.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.student.entity.Student;

public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    /** Stands in for "no student to exclude" - never a real id. */
    UUID NO_EXCLUSION = UUID.fromString("00000000-0000-0000-0000-000000000000");

    long countByActiveTrue();

    boolean existsByNameAndIdNot(String name, UUID id);

    /** The student record for a claimed login account, within the current tenant. */
    Optional<Student> findByUserId(UUID userId);

    /** Active students assigned to a group - the audience of a scheduled exam. */
    List<Student> findByGroup_IdAndActiveTrue(UUID groupId);

    // JPQL: Hibernate's @TenantId scopes these to the current workspace, so the
    // form only ever suggests this admin's own schools/cities.
    @Query("SELECT DISTINCT s.school FROM Student s WHERE s.school IS NOT NULL ORDER BY s.school")
    List<String> findDistinctSchools();

    @Query("SELECT DISTINCT s.city FROM Student s WHERE s.city IS NOT NULL ORDER BY s.city")
    List<String> findDistinctCities();

    /**
     * The highest serial ever issued, across all workspaces. Serial is a single
     * global sequence, so the "next code" preview must see past the tenant filter
     * - hence native SQL, which @TenantId does not touch.
     */
    @Query(value = "SELECT coalesce(max(serial), 0) FROM students", nativeQuery = true)
    int findMaxSerial();

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
}
