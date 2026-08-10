package com.center.lecture.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.center.lecture.entity.Lecture;

public interface LectureRepository extends JpaRepository<Lecture, UUID>, JpaSpecificationExecutor<Lecture> {

    interface GradeCount {
        String getGrade();

        long getCount();
    }

    /** Lesson counts per grade, for the filter tabs. */
    @Query("SELECT l.grade AS grade, count(l) AS count FROM Lecture l GROUP BY l.grade")
    List<GradeCount> countByGrade();
}
