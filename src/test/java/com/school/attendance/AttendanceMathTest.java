package com.school.attendance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain numbers in, plain numbers out: no Spring, no database, no mocks. */
class AttendanceMathTest {

    @Test
    void noDaysCounted_is100Percent() {
        assertThat(AttendanceMath.percentage(0, 0, 0)).isEqualByComparingTo("100.0");
        assertThat(AttendanceMath.percentage(0, 0, 0).scale()).isEqualTo(1);
    }

    @Test
    void lateCountsAsAttended() {
        // 1 present + 1 late of 2 counted days = 100%; 1 late + 1 absent = 50%.
        assertThat(AttendanceMath.percentage(1, 1, 0)).isEqualByComparingTo("100.0");
        assertThat(AttendanceMath.percentage(0, 1, 1)).isEqualByComparingTo("50.0");
    }

    @Test
    void percentageIsRoundedToOneDecimal() {
        assertThat(AttendanceMath.percentage(2, 0, 1)).isEqualByComparingTo("66.7"); // 66.666...
        assertThat(AttendanceMath.percentage(1, 0, 2)).isEqualByComparingTo("33.3"); // 33.333...
        assertThat(AttendanceMath.percentage(1, 0, 7)).isEqualByComparingTo("12.5"); // exact
        assertThat(AttendanceMath.percentage(1, 0, 15)).isEqualByComparingTo("6.3");  // 6.25 -> HALF_UP
        assertThat(AttendanceMath.percentage(2, 0, 1).scale()).isEqualTo(1);
    }

    @Test
    void lowThreshold_boundary() {
        assertThat(AttendanceMath.isLow(new BigDecimal("74.9"), 75)).isTrue();
        assertThat(AttendanceMath.isLow(new BigDecimal("75.0"), 75)).isFalse();
        assertThat(AttendanceMath.isLow(new BigDecimal("100.0"), 75)).isFalse();
        // 3 attended of 4 counted is exactly 75.0: not low. 749 of 1000 is 74.9: low.
        assertThat(AttendanceMath.isLow(AttendanceMath.percentage(3, 0, 1), 75)).isFalse();
        assertThat(AttendanceMath.isLow(AttendanceMath.percentage(749, 0, 251), 75)).isTrue();
    }

    @Test
    void excusedDaysAreNotPartOfTheFormula() {
        // The formula has no excused input: 3 present + 1 absent is 75.0 however many days were excused.
        assertThat(AttendanceMath.percentage(3, 0, 1)).isEqualByComparingTo("75.0");
    }
}
