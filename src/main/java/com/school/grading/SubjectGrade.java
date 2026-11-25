package com.school.grading;

import java.math.BigDecimal;

public record SubjectGrade(
        BigDecimal obtained,
        int fullMarks,
        BigDecimal percentage,
        String letter,
        BigDecimal point,
        boolean passed,
        boolean absent
) {
}
