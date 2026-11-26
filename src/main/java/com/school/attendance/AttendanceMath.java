package com.school.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure attendance rules: no database, no Spring, no clock. Same input, same output,
 * so every rule can be tested with plain numbers.
 */
public final class AttendanceMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AttendanceMath() {
    }

    /**
     * (present + late) / (present + late + absent) x 100, one decimal, HALF_UP.
     * Excused days are not counted at all. Nothing counted yet = 100.0 (nobody is flagged before day one).
     */
    public static BigDecimal percentage(long present, long late, long absent) {
        long attended = present + late;
        long counted = attended + absent;
        if (counted == 0) {
            return new BigDecimal("100.0");
        }
        return BigDecimal.valueOf(attended)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(counted), 1, RoundingMode.HALF_UP);
    }

    /** Low means strictly below the threshold: 74.9 is low, 75.0 is not. */
    public static boolean isLow(BigDecimal percentage, int thresholdPercent) {
        return percentage.compareTo(BigDecimal.valueOf(thresholdPercent)) < 0;
    }
}
