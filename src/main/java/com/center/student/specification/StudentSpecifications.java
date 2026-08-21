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
     * The one student-search rule the whole system uses, decided by the first
     * character typed:
     *
     * <ul>
     *   <li>starts with {@code 0} - a phone number. Matches the student's numbers
     *       and the guardian's, anywhere in the number.</li>
     *   <li>starts with any other digit - the student CODE, matched as a prefix,
     *       which is what a barcode scan produces.</li>
     *   <li>anything else - a name, school or residential area, matched
     *       anywhere.</li>
     * </ul>
     *
     * <p>Splitting on the leading zero is what makes the box unambiguous: every
     * Egyptian mobile begins with one and no student code does, so a digit string
     * can only be one of the two, and typing "12" narrows to codes instead of
     * dredging up every number containing 12. The mirror the browser searches
     * offline applies the same three rules, so the same text finds the same
     * students whether the line is up or not.
     *
     * <p>A null/blank term widens rather than excludes.
     */
    private static Specification<Student> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String cleaned = term.strip().toLowerCase();

        if (cleaned.startsWith("0")) {
            return phone(cleaned);
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            return serialPrefix(cleaned);
        }

        String contains = "%" + cleaned + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), contains),
                cb.like(cb.lower(root.get("school")), contains),
                cb.like(cb.lower(root.get("city")), contains));
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
