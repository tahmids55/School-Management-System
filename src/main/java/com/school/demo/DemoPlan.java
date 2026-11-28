package com.school.demo;

import com.school.attendance.AttendanceStatus;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The pure rules of the demo school: no database, no Spring. Every random choice takes the caller's
 * {@link Random}, so with {@code new Random(42)} and the same "today" the result is always the same school.
 */
public final class DemoPlan {

    /** How many school days of attendance are seeded. */
    static final int SCHOOL_DAYS = 30;

    private DemoPlan() {
    }

    /** Friday and Saturday are the weekend in Bangladesh. */
    static boolean isSchoolDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.FRIDAY && day != DayOfWeek.SATURDAY;
    }

    /** The last {@code count} school days up to and including today, never before the year starts; oldest first. */
    static List<LocalDate> lastSchoolDays(LocalDate today, LocalDate yearStart, int count) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate date = today; days.size() < count && !date.isBefore(yearStart); date = date.minusDays(1)) {
            if (isSchoolDay(date)) {
                days.add(date);
            }
        }
        Collections.reverse(days);
        return days;
    }

    /**
     * A normal student: about 88 % present, 5 % late, 5 % absent, 2 % excused.
     * The weak student (roll 5) attends only about 62 % of the counted days, so the low-attendance warning shows.
     */
    static AttendanceStatus attendanceStatus(Random random, boolean weak) {
        int roll = random.nextInt(100);
        if (weak) {
            if (roll < 58) return AttendanceStatus.PRESENT;
            if (roll < 62) return AttendanceStatus.LATE;
            if (roll < 98) return AttendanceStatus.ABSENT;
            return AttendanceStatus.EXCUSED;
        }
        if (roll < 88) return AttendanceStatus.PRESENT;
        if (roll < 93) return AttendanceStatus.LATE;
        if (roll < 98) return AttendanceStatus.ABSENT;
        return AttendanceStatus.EXCUSED;
    }

    /** How good a student is, 0.35 to 0.95 of full marks, around 0.68. Fixed per student for every exam. */
    static double ability(Random random) {
        return clamp(0.68 + random.nextGaussian() * 0.13, 0.35, 0.95);
    }

    /** About 2 % of the sheets are "absent". */
    static boolean absentFromExam(Random random) {
        return random.nextInt(100) < 2;
    }

    /** Whole marks near the student's ability for one subject, never below 0 or above full marks. */
    static BigDecimal marks(Random random, double ability, int fullMarks) {
        double share = clamp(ability + random.nextGaussian() * 0.08, 0.0, 1.0);
        return BigDecimal.valueOf(Math.round(share * fullMarks));
    }

    /** Monthly tuition is billed for the last six months of the year: July to December. */
    static List<YearMonth> tuitionMonths(int year) {
        List<YearMonth> months = new ArrayList<>();
        for (int month = 7; month <= 12; month++) {
            months.add(YearMonth.of(year, month));
        }
        return months;
    }

    enum PaymentState { PAID, PART_PAID, UNPAID }

    /**
     * How a tuition invoice stands today. Not due yet = unpaid (nobody pays in the future).
     * Due in the last 60 days ("recent") is often unpaid; older invoices are mostly paid.
     */
    static PaymentState tuitionState(Random random, LocalDate dueDate, LocalDate today) {
        int roll = random.nextInt(100);
        if (dueDate.isAfter(today)) {
            return PaymentState.UNPAID;
        }
        boolean recent = dueDate.isAfter(today.minusDays(60));
        if (recent) {
            if (roll < 45) return PaymentState.PAID;
            if (roll < 65) return PaymentState.PART_PAID;
            return PaymentState.UNPAID;
        }
        if (roll < 85) return PaymentState.PAID;
        if (roll < 95) return PaymentState.PART_PAID;
        return PaymentState.UNPAID;
    }

    /** 10:00 at the school on a day 0-7 days before the due date, but never later than now. */
    static Instant paidAt(Random random, LocalDate dueDate, ZoneId zone, Instant now) {
        LocalDate day = dueDate.minusDays(random.nextInt(8));
        Instant paidAt = day.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant();
        return paidAt.isAfter(now) ? now : paidAt;
    }

    /** The earlier of the two: seeded dates in the past stay as they are, a future one becomes now. */
    static Instant notAfter(Instant instant, Instant now) {
        return instant.isAfter(now) ? now : instant;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
