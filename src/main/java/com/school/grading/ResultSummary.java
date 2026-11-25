package com.school.grading;

import java.math.BigDecimal;

public record ResultSummary(
        BigDecimal totalObtained,
        int totalFullMarks,
        BigDecimal percentage,
        BigDecimal gpa,
        String letter,
        boolean passed
) {
}
