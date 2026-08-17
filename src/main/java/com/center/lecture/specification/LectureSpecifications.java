package com.center.lecture.specification;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.domain.Specification;

import com.center.lecture.dto.LectureFilter;
import com.center.lecture.entity.Lecture;

public final class LectureSpecifications {

    private LectureSpecifications() {
    }

    public static Specification<Lecture> matching(LectureFilter filter) {
        return Specification.allOf(
                search(filter.search()),
                grade(filter.grade()),
                hasExam(filter.hasExam()),
                createdWithin(filter.withinDays()));
    }

    private static Specification<Lecture> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.strip().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("examName")), pattern));
    }

    private static Specification<Lecture> grade(String grade) {
        return grade == null || grade.isBlank()
                ? null
                : (root, query, cb) -> cb.equal(root.get("grade"), grade);
    }

    private static Specification<Lecture> hasExam(Boolean hasExam) {
        return hasExam == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("hasExam"), hasExam);
    }

    /**
     * Lessons created in the last N days.
     *
     * <p>The cut-off is computed here rather than sent by the client, so "the
     * last 7 days" cannot drift with a device whose clock is wrong.
     */
    private static Specification<Lecture> createdWithin(Integer days) {
        if (days == null || days <= 0) {
            return null;
        }
        OffsetDateTime since = OffsetDateTime.now().minusDays(days);
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), since);
    }
}
