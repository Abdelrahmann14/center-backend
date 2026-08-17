package com.center.lecture.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.lecture.entity.Lecture;

public interface LectureRepository extends JpaRepository<Lecture, UUID>, JpaSpecificationExecutor<Lecture> {

    interface GradeCount {
        String getGrade();

        long getCount();
    }

    /** Lesson counts per grade, for the filter tabs. */
    @Query("SELECT l.grade AS grade, count(l) AS count FROM Lecture l GROUP BY l.grade")
    List<GradeCount> countByGrade();

    /**
     * True when another lesson in the same grade already carries this name
     * (case-insensitive). Scoped to the workspace by @TenantId. {@code excludeId}
     * lets an edit keep its own name.
     */
    @Query("""
            SELECT count(l) > 0 FROM Lecture l
            WHERE l.grade = :grade AND lower(l.name) = lower(:name) AND l.id <> :excludeId
            """)
    boolean existsDuplicateName(@Param("grade") String grade, @Param("name") String name,
            @Param("excludeId") UUID excludeId);
}
