package com.school.grading;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class Gpa4GradingStrategy implements GradingStrategy {

    public static final String CODE = "GPA_4";
    public static final String DISPLAY_NAME = "GPA 4.0 (International)";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_2 = new BigDecimal("0.00");
    private static final GradeBand FAIL_BAND = new GradeBand(0, "F", ZERO_2);

    private static final List<GradeBand> BANDS = List.of(
            new GradeBand(90, "A", new BigDecimal("4.00")),
            new GradeBand(80, "B", new BigDecimal("3.00")),
            new GradeBand(70, "C", new BigDecimal("2.00")),
            new GradeBand(60, "D", new BigDecimal("1.00")),
            FAIL_BAND
    );

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public SubjectGrade gradeSubject(BigDecimal obtained, int fullMarks, int passMarks, boolean absent) {
        if (fullMarks <= 0) {
            throw new IllegalArgumentException("fullMarks must be positive");
        }
        if (passMarks < 0) {
            throw new IllegalArgumentException("passMarks must not be negative");
        }
        if (absent) {
            return new SubjectGrade(null, fullMarks, ZERO_2, "F", ZERO_2, false, true);
        }

        BigDecimal safeObtained = obtained == null ? BigDecimal.ZERO : obtained;
        BigDecimal percentage = safeObtained
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(fullMarks), 2, RoundingMode.HALF_UP);

        if (safeObtained.compareTo(BigDecimal.valueOf(passMarks)) < 0) {
            return new SubjectGrade(safeObtained, fullMarks, percentage, "F", ZERO_2, false, false);
        }

        GradeBand band = bandForPercentage(percentage);
        boolean passed = band.point().compareTo(BigDecimal.ZERO) > 0;
        return new SubjectGrade(safeObtained, fullMarks, percentage, band.letter(), band.point(), passed, false);
    }

    @Override
    public ResultSummary summarize(List<SubjectGrade> grades) {
        if (grades == null || grades.isEmpty()) {
            return new ResultSummary(BigDecimal.ZERO, 0, ZERO_2, ZERO_2, "F", false);
        }

        BigDecimal totalObtained = BigDecimal.ZERO;
        int totalFullMarks = 0;
        BigDecimal totalPoints = BigDecimal.ZERO;
        boolean allPassed = true;

        for (SubjectGrade grade : grades) {
            if (grade.obtained() != null) {
                totalObtained = totalObtained.add(grade.obtained());
            }
            totalFullMarks += grade.fullMarks();
            totalPoints = totalPoints.add(grade.point());
            if (!grade.passed()) {
                allPassed = false;
            }
        }

        BigDecimal percentage = totalFullMarks > 0
                ? totalObtained.multiply(HUNDRED).divide(BigDecimal.valueOf(totalFullMarks), 2, RoundingMode.HALF_UP)
                : ZERO_2;

        BigDecimal gpa = totalPoints.divide(BigDecimal.valueOf(grades.size()), 2, RoundingMode.HALF_UP);
        String letter = letterForGpa(gpa);
        return new ResultSummary(totalObtained, totalFullMarks, percentage, gpa, letter, allPassed);
    }

    private static GradeBand bandForPercentage(BigDecimal percentage) {
        for (GradeBand band : BANDS) {
            if (percentage.compareTo(BigDecimal.valueOf(band.minPercent())) >= 0) {
                return band;
            }
        }
        return FAIL_BAND;
    }

    private static String letterForGpa(BigDecimal gpa) {
        if (gpa.compareTo(new BigDecimal("3.50")) >= 0) {
            return "A";
        }
        if (gpa.compareTo(new BigDecimal("2.50")) >= 0) {
            return "B";
        }
        if (gpa.compareTo(new BigDecimal("1.50")) >= 0) {
            return "C";
        }
        if (gpa.compareTo(new BigDecimal("1.00")) >= 0) {
            return "D";
        }
        return "F";
    }
}
