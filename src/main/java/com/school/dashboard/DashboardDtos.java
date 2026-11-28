package com.school.dashboard;

import com.school.exam.ExamDtos.MyExamResult;
import com.school.fee.FeeDtos.FeeSummary;
import com.school.fee.PaymentMethod;
import com.school.notice.NoticeDtos.NoticeResponse;
import com.school.teacher.TeacherDtos.TeachingLoad;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Read-only shapes of the dashboards. Every list is already sorted for the charts. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Counts(
            long students,
            long teachers,
            long classes,
            long subjects
    ) {
    }

    /** School-wide attendance of one day that has records. */
    public record TrendPoint(
            LocalDate date,
            BigDecimal percentage
    ) {
    }

    /** Money collected in one month, month labelled "Nov 2026". */
    public record MonthAmount(
            String month,
            BigDecimal amount
    ) {
    }

    /** One bar or pie slice: "Class 9 - A" / 32, "Female" / 120, "A+" / 14. */
    public record NameValue(
            String name,
            long value
    ) {
    }

    public record RecentPayment(
            String studentName,
            String invoiceNo,
            BigDecimal amount,
            PaymentMethod method,
            Instant paidAt
    ) {
    }

    public record AdminDashboard(
            String academicYear,
            Counts counts,
            BigDecimal todayAttendance,
            List<TrendPoint> attendanceTrend,
            FeeSummary fees,
            List<MonthAmount> monthlyCollection,
            List<NameValue> classStrength,
            List<NameValue> genderSplit,
            String latestExam,
            List<NameValue> gradeDistribution,
            List<RecentPayment> recentPayments,
            List<NoticeResponse> notices
    ) {
    }

    public record ClassToday(
            Long classId,
            String className,
            long students,
            boolean attendanceTaken
    ) {
    }

    public record TeacherDashboard(
            String teacherName,
            TeachingLoad teachingLoad,
            List<ClassToday> classesToday,
            List<NoticeResponse> notices
    ) {
    }

    /**
     * The logged-in student's home (F31). Attendance covers 1 January of this year up to today.
     * className, rollNo and latestResult are null (left out of the JSON) when there is no class or no result.
     */
    public record StudentDashboard(
            String studentName,
            String className,
            Integer rollNo,
            BigDecimal attendancePercentage,
            boolean lowAttendance,
            /** present + late: a late student still attended. */
            long daysPresent,
            long daysAbsent,
            MyExamResult latestResult,
            BigDecimal outstandingFees,
            List<NoticeResponse> notices
    ) {
    }
}
