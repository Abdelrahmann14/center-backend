package com.center.student.specification;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.center.student.dto.StudentFilter;
import com.center.student.entity.Student;
import com.center.common.enums.AcademicTrack;
import com.center.common.enums.Gender;
import com.center.common.enums.Religion;

/** Composable filters for the paginated student list. */
public final class StudentSpecifications {

    private StudentSpecifications() {
    }

    public static Specification<Student> matching(StudentFilter filter) {
        return Specification.allOf(
                search(filter.search()),
                serialPrefix(filter.serial()),
                name(filter.name()),
                phone(filter.phone()),
                grade(filter.grade()),
                group(filter.groupId()),
                gender(filter.gender()),
                academicTrack(filter.academicTrack()),
                active(filter.active()),
                religion(filter.religion()));
    }

    private static Specification<Student> religion(Religion religion) {
        return religion == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("religion"), religion);
    }

    /** Serial as a leading-digits match, with no phone noise. */
    private static Specification<Student> serialPrefix(String serial) {
        if (serial == null || serial.isBlank()) {
            return null;
        }
        String prefix = serial.strip() + "%";
        // concat with "" forces the cast Postgres needs; a bare .as(String) is a
        // no-op at the SQL level and leaves `integer LIKE text`.
        return (root, query, cb) -> cb.like(
                cb.concat(root.get("serial").as(String.class), ""), prefix);
    }

    /** The name and nothing else - the registration box's Arabic-letters mode. */
    private static Specification<Student> name(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String contains = "%" + name.strip().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), contains);
    }

    /**
     * Any of the student's or the parent's numbers - the registration box's
     * leading-zero mode.
     */
    private static Specification<Student> phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String contains = "%" + phone.strip() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(phonesAsText(root.get("studentPhones"), cb), contains),
                cb.like(phonesAsText(root.get("parentPhones"), cb), contains));
    }

    /**
     * Matches name, school, city, any phone number, or the serial as a prefix.
     * A null/blank term widens rather than excludes.
     */
    private static Specification<Student> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String cleaned = term.strip().toLowerCase();
        String contains = "%" + cleaned + "%";
        // The serial (student code) is matched as a prefix, mirroring the old
        // client search where typing a code narrowed by leading digits.
        String serialPrefix = cleaned + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), contains),
                cb.like(cb.lower(root.get("school")), contains),
                cb.like(cb.lower(root.get("city")), contains),
                cb.like(cb.concat(root.get("serial").as(String.class), ""), serialPrefix),
                // The phones are text[]; flatten each array to a string so a
                // partial number matches any entry in it.
                cb.like(phonesAsText(root.get("studentPhones"), cb), contains),
                cb.like(phonesAsText(root.get("parentPhones"), cb), contains));
    }

    private static jakarta.persistence.criteria.Expression<String> phonesAsText(
            jakarta.persistence.criteria.Expression<?> phones,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.function("array_to_string", String.class, phones, cb.literal(" "));
    }

    private static Specification<Student> grade(String grade) {
        return grade == null || grade.isBlank()
                ? null
                : (root, query, cb) -> cb.equal(root.get("grade"), grade);
    }

    private static Specification<Student> group(UUID groupId) {
        return groupId == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("group").get("id"), groupId);
    }

    private static Specification<Student> gender(Gender gender) {
        return gender == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("gender"), gender);
    }

    private static Specification<Student> academicTrack(AcademicTrack track) {
        return track == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("academicTrack"), track);
    }

    private static Specification<Student> active(Boolean active) {
        return active == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
