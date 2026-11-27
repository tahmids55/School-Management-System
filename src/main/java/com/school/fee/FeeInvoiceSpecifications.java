package com.school.fee;

import com.school.student.Student;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Dynamic search filters for fee invoices.
 */
final class FeeInvoiceSpecifications {

    private FeeInvoiceSpecifications() {
    }

    static Specification<FeeInvoice> search(
            String q,
            InvoiceStatus status,
            Long classId,
            boolean overdue,
            LocalDate today
    ) {
        return Specification.allOf(
                matches(q),
                hasStatus(status),
                inClass(classId),
                isOverdue(overdue, today)
        );
    }

    static Specification<FeeInvoice> matches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Join<FeeInvoice, Student> student = root.join("student", JoinType.INNER);
            Expression<String> fullName = cb.lower(
                    cb.concat(cb.concat(student.get("firstName"), " "), student.get("lastName")));
            return cb.or(
                    cb.like(cb.lower(root.get("invoiceNo")), pattern, '\\'),
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(student.get("admissionNo")), pattern, '\\'),
                    cb.like(fullName, pattern, '\\')
            );
        };
    }

    static Specification<FeeInvoice> hasStatus(InvoiceStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<FeeInvoice> inClass(Long classId) {
        if (classId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("student").get("schoolClass").get("id"), classId);
    }

    static Specification<FeeInvoice> isOverdue(boolean overdue, LocalDate today) {
        if (!overdue) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                root.get("status").in(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID),
                cb.lessThan(root.get("dueDate"), today)
        );
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
