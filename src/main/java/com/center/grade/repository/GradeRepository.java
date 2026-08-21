package com.center.grade.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.center.grade.entity.Grade;

public interface GradeRepository extends JpaRepository<Grade, UUID> {

    /** School-year order; name breaks ties so the list never shuffles. */
    List<Grade> findAllByOrderBySortOrderAscNameAsc();

    /**
     * The active grades this workspace actually teaches: the ones priced by a
     * center or already carrying a group.
     *
     * <p>The full master list is every grade the platform knows, which is not the
     * question a student form asks - a teacher who only takes ثانوي should not be
     * offered إعدادي. Groups count as well as prices, so a grade that is taught
     * but not yet priced is still offered rather than silently unreachable.
     *
     * <p>Both {@code CenterGrade} and {@code Group} are tenant-scoped, so the
     * subqueries see only this workspace and the answer differs per teacher.
     */
    @Query("""
            SELECT g FROM Grade g
            WHERE g.active = true
              AND (g.name IN (SELECT cg.id.grade FROM CenterGrade cg)
                   OR g.name IN (SELECT gr.grade FROM Group gr))
            ORDER BY g.sortOrder ASC, g.name ASC
            """)
    List<Grade> findInUse();

    Optional<Grade> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /**
     * How many students sit in each grade across the whole platform.
     *
     * <p>Native, and deliberately so: students are tenant-scoped, and this is the
     * one caller that wants the total rather than one workspace's share. Students
     * name their grade as text, so the join is by name - the same key the student
     * form writes.
     */
    @Query(value = """
            SELECT s.grade AS grade, count(*) AS total
            FROM students s
            WHERE s.grade IS NOT NULL
            GROUP BY s.grade
            """, nativeQuery = true)
    List<Object[]> countStudentsByGrade();
}
