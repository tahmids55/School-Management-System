package com.school.student;

import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Locale;

/**
 * Dynamic search filters. Each method returns one WHERE condition, or null for "no filter";
 * Specification.allOf(...) skips the nulls, so only the filters the user filled in reach the SQL.
 */
final class StudentSpecifications {

    private StudentSpecifications() {
    }

    static Specification<Student> search(String q, Long classId, StudentStatus status) {
        return Specification.allOf(matches(q), inClass(classId), hasStatus(status));
    }

    static Specification<Student> inClass(Long classId) {
        if (classId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("schoolClass").get("id"), classId);
    }

    /** Only students of these classes (a teacher's own classes). An empty set must be handled before. */
    static Specification<Student> inClasses(Collection<Long> classIds) {
        return (root, query, cb) -> root.get("schoolClass").get("id").in(classIds);
    }

    static Specification<Student> hasStatus(StudentStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Part of the full name (any case), of the admission no, or of the guardian phone. */
    static Specification<Student> matches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String text = escapeLike(q.trim());
        return (root, query, cb) -> {
            Expression<String> fullName = cb.lower(
                    cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName")));
            return cb.or(
                    cb.like(fullName, "%" + text.toLowerCase(Locale.ROOT) + "%", '\\'),
                    cb.like(root.get("admissionNo"), "%" + text.toUpperCase(Locale.ROOT) + "%", '\\'),
                    cb.like(root.get("guardianPhone"), "%" + text + "%", '\\'));
        };
    }

    /** Typing "%" or "_" searches for that character instead of acting as a wildcard. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
