package com.school.demo;

import com.school.attendance.AttendanceStatus;
import com.school.demo.DemoPlan.PaymentState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPlanTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    /** Thursday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test
    void lastSchoolDays_are30_withoutFridayOrSaturday_oldestFirst_endingToday() {
        List<LocalDate> days = DemoPlan.lastSchoolDays(TODAY, LocalDate.of(2026, 1, 1), 30);

        assertThat(days).hasSize(30);
        assertThat(days.get(29)).isEqualTo(TODAY);
        assertThat(days).isSorted();
        assertThat(days).noneMatch(d -> d.getDayOfWeek() == DayOfWeek.FRIDAY || d.getDayOfWeek() == DayOfWeek.SATURDAY);
        // 30 school days = 6 weeks of 5 days: back to Sunday 18 Oct.
        assertThat(days.get(0)).isEqualTo(LocalDate.of(2026, 10, 18));
    }

    @Test
    void lastSchoolDays_onSaturday_startsWithThursday_andNeverBeforeTheYear() {
        List<LocalDate> saturday = DemoPlan.lastSchoolDays(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 1, 1), 30);
        assertThat(saturday.get(29)).isEqualTo(LocalDate.of(2026, 11, 26));

        // In early January there are fewer than 30 school days in the year: only those are used.
        List<LocalDate> january = DemoPlan.lastSchoolDays(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 1), 30);
        assertThat(january).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6));
    }

    @Test
    void attendanceStatus_normalStudent_isAbout88_5_5_2() {
        Map<AttendanceStatus, Integer> counts = draw(false, 20_000);

        assertThat(share(counts, AttendanceStatus.PRESENT)).isBetween(0.86, 0.90);
        assertThat(share(counts, AttendanceStatus.LATE)).isBetween(0.04, 0.06);
        assertThat(share(counts, AttendanceStatus.ABSENT)).isBetween(0.04, 0.06);
        assertThat(share(counts, AttendanceStatus.EXCUSED)).isBetween(0.01, 0.03);
    }

    @Test
    void attendanceStatus_weakStudent_attendsAbout62Percent_belowTheWarningLevel() {
        Map<AttendanceStatus, Integer> counts = draw(true, 20_000);
        double attended = counts.get(AttendanceStatus.PRESENT) + counts.get(AttendanceStatus.LATE);
        double counted = attended + counts.get(AttendanceStatus.ABSENT);

        assertThat(share(counts, AttendanceStatus.PRESENT) + share(counts, AttendanceStatus.LATE)).isBetween(0.60, 0.64);
        assertThat(attended / counted).isLessThan(0.75);
    }

    @Test
    void sameSeed_givesTheSameSequence() {
        Random a = new Random(42);
        Random b = new Random(42);
        List<Object> first = new ArrayList<>();
        List<Object> second = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            first.add(DemoPlan.attendanceStatus(a, i % 7 == 0));
            first.add(DemoPlan.marks(a, DemoPlan.ability(a), 100));
            second.add(DemoPlan.attendanceStatus(b, i % 7 == 0));
            second.add(DemoPlan.marks(b, DemoPlan.ability(b), 100));
        }
        assertThat(first).isEqualTo(second);
    }

    @Test
    void marks_areWholeNumbers_between0AndFullMarks() {
        Random random = new Random(42);
        for (int i = 0; i < 5_000; i++) {
            int full = i % 2 == 0 ? 100 : 50;
            BigDecimal marks = DemoPlan.marks(random, DemoPlan.ability(random), full);
            assertThat(marks.scale()).isZero();
            assertThat(marks).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(full));
        }
    }

    @Test
    void ability_staysBetween35And95Percent() {
        Random random = new Random(42);
        for (int i = 0; i < 5_000; i++) {
            assertThat(DemoPlan.ability(random)).isBetween(0.35, 0.95);
        }
    }

    @Test
    void absentFromExam_isAbout2Percent() {
        Random random = new Random(42);
        int absent = 0;
        for (int i = 0; i < 20_000; i++) {
            if (DemoPlan.absentFromExam(random)) absent++;
        }
        assertThat(absent / 20_000.0).isBetween(0.01, 0.03);
    }

    @Test
    void tuitionMonths_areJulyToDecember() {
        assertThat(DemoPlan.tuitionMonths(2026)).containsExactly(
                YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9),
                YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12));
    }

    @Test
    void tuitionState_notDueYet_isAlwaysUnpaid() {
        Random random = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            assertThat(DemoPlan.tuitionState(random, LocalDate.of(2026, 12, 10), TODAY)).isEqualTo(PaymentState.UNPAID);
        }
    }

    @Test
    void tuitionState_olderIsMostlyPaid_recentIsOftenUnpaid() {
        Random random = new Random(42);
        int olderPaid = 0;
        int recentUnpaid = 0;
        for (int i = 0; i < 10_000; i++) {
            if (DemoPlan.tuitionState(random, LocalDate.of(2026, 7, 10), TODAY) == PaymentState.PAID) olderPaid++;
            if (DemoPlan.tuitionState(random, LocalDate.of(2026, 11, 10), TODAY) == PaymentState.UNPAID) recentUnpaid++;
        }
        assertThat(olderPaid / 10_000.0).isBetween(0.82, 0.88);
        assertThat(recentUnpaid / 10_000.0).isBetween(0.32, 0.38);
    }

    @Test
    void paidAt_isUpTo7DaysBeforeDue_at10InDhaka_andNeverAfterNow() {
        Instant now = Instant.parse("2026-11-26T04:00:00Z");
        Random random = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            Instant paidAt = DemoPlan.paidAt(random, LocalDate.of(2026, 9, 10), DHAKA, now);
            LocalDate day = paidAt.atZone(DHAKA).toLocalDate();
            assertThat(day).isBetween(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 10));
            assertThat(paidAt.atZone(DHAKA).getHour()).isEqualTo(10);
        }
        // Due today at 10:00, but it is only 08:00 now: the payment is dated now, not in the future.
        Instant early = Instant.parse("2026-11-26T02:00:00Z");
        for (int i = 0; i < 1_000; i++) {
            assertThat(DemoPlan.paidAt(random, TODAY, DHAKA, early)).isBeforeOrEqualTo(early);
        }
    }

    private static Map<AttendanceStatus, Integer> draw(boolean weak, int times) {
        Random random = new Random(42);
        Map<AttendanceStatus, Integer> counts = new EnumMap<>(AttendanceStatus.class);
        for (AttendanceStatus status : AttendanceStatus.values()) {
            counts.put(status, 0);
        }
        for (int i = 0; i < times; i++) {
            counts.merge(DemoPlan.attendanceStatus(random, weak), 1, Integer::sum);
        }
        return counts;
    }

    private static double share(Map<AttendanceStatus, Integer> counts, AttendanceStatus status) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return counts.get(status) / (double) total;
    }
}
