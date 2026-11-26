package com.school.exam;

import com.school.academic.Subject;
import com.school.common.exception.BusinessRuleException;
import com.school.exam.ExamDtos.MarkEntry;

import java.math.BigDecimal;

/**
 * Pure validation helper for a single mark entry against a subject's full marks and decimal rules.
 */
public final class MarkValidator {

    private MarkValidator() {
    }

    public static BigDecimal validateEntry(MarkEntry entry, Subject subject) {
        if (entry.absent()) {
            return null;
        }
        BigDecimal marks = entry.marksObtained();
        if (marks == null) {
            throw new BusinessRuleException(
                    "Enter marks or mark the student absent (student " + entry.studentId() + ")"
            );
        }
        if (marks.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Marks can have at most 2 decimal places");
        }
        BigDecimal maxMarks = BigDecimal.valueOf(subject.getFullMarks());
        if (marks.compareTo(BigDecimal.ZERO) < 0 || marks.compareTo(maxMarks) > 0) {
            throw new BusinessRuleException(
                    "Marks must be between 0 and " + subject.getFullMarks() + " for " + subject.getCode()
            );
        }
        return marks;
    }
}
