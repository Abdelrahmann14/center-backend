package com.center.lecture.specification;

import org.springframework.data.jpa.domain.Specification;

import com.center.lecture.dto.LectureFilter;
import com.center.lecture.entity.Lecture;

public final class LectureSpecifications {

    private LectureSpecifications() {
    }

    public static Specification<Lecture> matching(LectureFilter filter) {
        return Specification.allOf(search(filter.search()), grade(filter.grade()));
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
}
