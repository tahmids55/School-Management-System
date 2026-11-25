package com.school.grading;

import java.math.BigDecimal;
import java.util.List;

/**
 * Hand-written Singleton holding the official Bangladesh secondary grading scale.
 */
public final class BangladeshGradeScale {

    private static final GradeBand FAIL_BAND = new GradeBand(0, "F", new BigDecimal("0.00"));

    private static final BangladeshGradeScale INSTANCE = new BangladeshGradeScale();

    private final List<GradeBand> bands = List.of(
            new GradeBand(80, "A+", new BigDecimal("5.00")),
            new GradeBand(70, "A", new BigDecimal("4.00")),
            new GradeBand(60, "A-", new BigDecimal("3.50")),
            new GradeBand(50, "B", new BigDecimal("3.00")),
            new GradeBand(40, "C", new BigDecimal("2.00")),
            new GradeBand(33, "D", new BigDecimal("1.00")),
            FAIL_BAND
    );

    private BangladeshGradeScale() {
    }

    public static BangladeshGradeScale getInstance() {
        return INSTANCE;
    }

    public List<GradeBand> bands() {
        return bands;
    }

    public GradeBand forPercentage(BigDecimal percentage) {
        if (percentage == null) {
            return FAIL_BAND;
        }
        for (GradeBand band : bands) {
            if (percentage.compareTo(BigDecimal.valueOf(band.minPercent())) >= 0) {
                return band;
            }
        }
        return FAIL_BAND;
    }

    /**
     * Returns the first band whose grade point is less than or equal to the given GPA.
     */
    public String letterForGpa(BigDecimal gpa) {
        if (gpa == null) {
            return FAIL_BAND.letter();
        }
        for (GradeBand band : bands) {
            if (band.point().compareTo(gpa) <= 0) {
                return band.letter();
            }
        }
        return FAIL_BAND.letter();
    }
}
