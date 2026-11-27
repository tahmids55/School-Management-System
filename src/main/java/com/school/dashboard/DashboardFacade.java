package com.school.dashboard;

import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.SubjectRepository;
import com.school.attendance.AttendanceMath;
import com.school.attendance.AttendanceStatus;
import com.school.dashboard.DashboardDtos.AdminDashboard;
import com.school.dashboard.DashboardDtos.ClassToday;
import com.school.dashboard.DashboardDtos.Counts;
import com.school.dashboard.DashboardDtos.MonthAmount;
import com.school.dashboard.DashboardDtos.NameValue;
import com.school.dashboard.DashboardDtos.RecentPayment;
import com.school.dashboard.DashboardDtos.TeacherDashboard;
import com.school.dashboard.DashboardDtos.TrendPoint;
import com.school.exam.Exam;
import com.school.fee.FeeInvoiceService;
import com.school.fee.Payment;
import com.school.grading.GradingStrategy;
import com.school.grading.GradingStrategyResolver;
import com.school.grading.SubjectGrade;
import com.school.notice.NoticeDtos.NoticeResponse;
import com.school.notice.NoticeService;
import com.school.security.AccessGuard;
import com.school.student.Gender;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherDtos.ClassRef;
import com.school.teacher.TeacherDtos.SubjectAssignment;
import com.school.teacher.TeacherDtos.TeachingLoad;
import com.school.teacher.TeacherService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Facade pattern: ONE call per role builds a whole dashboard.
 * The controller does not know that the numbers come from seven modules (academic, students, teachers,
 * attendance, fees, exams + grading, notices); it only sees {@link #adminDashboard()} and
 * {@link #teacherDashboard()}. Existing services are reused for their rules, and the few new numbers come
 * from fixed, grouped queries in {@link DashboardRepository}.
 * The student part is added by F31.
 */
@Service
public class DashboardFacade {

    static final int TREND_DAYS = 14;
    static final int COLLECTION_MONTHS = 6;
    static final int RECENT_PAYMENTS = 8;
    static final int NOTICES = 5;
    /** Chart order of the letters, best first. */
    static final List<String> LETTER_ORDER = List.of("A+", "A", "A-", "B", "C", "D", "F");

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DashboardRepository dashboardRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final FeeInvoiceService feeInvoiceService;
    private final NoticeService noticeService;
    private final TeacherService teacherService;
    private final AccessGuard accessGuard;
    private final GradingStrategyResolver gradingStrategyResolver;
    private final Clock clock;

    public DashboardFacade(
            DashboardRepository dashboardRepository,
            AcademicYearRepository academicYearRepository,
            SchoolClassRepository schoolClassRepository,
            SubjectRepository subjectRepository,
            FeeInvoiceService feeInvoiceService,
            NoticeService noticeService,
            TeacherService teacherService,
            AccessGuard accessGuard,
            GradingStrategyResolver gradingStrategyResolver,
            Clock clock
    ) {
        this.dashboardRepository = dashboardRepository;
        this.academicYearRepository = academicYearRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.subjectRepository = subjectRepository;
        this.feeInvoiceService = feeInvoiceService;
        this.noticeService = noticeService;
        this.teacherService = teacherService;
        this.accessGuard = accessGuard;
        this.gradingStrategyResolver = gradingStrategyResolver;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public AdminDashboard adminDashboard() {
        LocalDate today = LocalDate.now(clock);
        AcademicYear year = academicYearRepository.findByCurrentTrue().orElse(null);
        List<SchoolClass> classes = year == null ? List.of() : schoolClassRepository.findAllByYearWithYear(year.getId());

        Counts counts = new Counts(
                dashboardRepository.countActiveStudents(),
                dashboardRepository.countActiveTeachers(),
                classes.size(),
                subjectRepository.count()
        );

        // One grouped query gives both today's number and the 14-day trend.
        List<TrendPoint> trend = attendanceTrend(
                dashboardRepository.countAttendanceByDateAndStatus(today.minusDays(TREND_DAYS - 1L), today));
        BigDecimal todayAttendance = trend.stream()
                .filter(point -> point.date().equals(today))
                .map(TrendPoint::percentage)
                .findFirst()
                .orElse(null);

        Exam latestExam = dashboardRepository.findLatestPublishedExams(PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElse(null);

        return new AdminDashboard(
                year == null ? null : year.getName(),
                counts,
                todayAttendance,
                trend,
                feeInvoiceService.summary(),
                monthlyCollection(),
                classStrength(classes),
                genderSplit(dashboardRepository.countActiveStudentsByGender()),
                latestExam == null ? null : latestExam.getName(),
                latestExam == null ? List.of() : gradeDistribution(latestExam.getId()),
                recentPayments(),
                latestNotices()
        );
    }

    /** Rows of [date, status, count] -> one point per recorded day, oldest first. Days without records are left out. */
    static List<TrendPoint> attendanceTrend(List<Object[]> rows) {
        Map<LocalDate, Map<AttendanceStatus, Long>> byDay = new TreeMap<>();
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            AttendanceStatus status = (AttendanceStatus) row[1];
            long count = ((Number) row[2]).longValue();
            byDay.computeIfAbsent(date, d -> new EnumMap<>(AttendanceStatus.class)).put(status, count);
        }
        List<TrendPoint> points = new ArrayList<>();
        byDay.forEach((date, counts) -> points.add(new TrendPoint(date, AttendanceMath.percentage(
                counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
                counts.getOrDefault(AttendanceStatus.LATE, 0L),
                counts.getOrDefault(AttendanceStatus.ABSENT, 0L)))));
        return points;
    }

    /** The last six months including this one, oldest first; months without payments are 0.00. */
    private List<MonthAmount> monthlyCollection() {
        ZoneId zone = clock.getZone();
        YearMonth first = YearMonth.now(clock).minusMonths(COLLECTION_MONTHS - 1L);
        Instant from = first.atDay(1).atStartOfDay(zone).toInstant();
        return fillMonths(first, dashboardRepository.sumPaymentsByMonth(from, zone.getId()));
    }

    /** Rows of ["YYYY-MM", sum] -> six labelled months, zero where nothing was paid. */
    static List<MonthAmount> fillMonths(YearMonth first, List<Object[]> rows) {
        Map<String, BigDecimal> sums = new HashMap<>();
        for (Object[] row : rows) {
            sums.put((String) row[0], (BigDecimal) row[1]);
        }
        List<MonthAmount> months = new ArrayList<>();
        for (int i = 0; i < COLLECTION_MONTHS; i++) {
            YearMonth month = first.plusMonths(i);
            BigDecimal amount = sums.getOrDefault(month.format(MONTH_KEY), BigDecimal.ZERO);
            months.add(new MonthAmount(month.format(MONTH_LABEL), amount.setScale(2, RoundingMode.HALF_UP)));
        }
        return months;
    }

    /** Active students of every class of the year (also empty classes), using the existing grouped count. */
    private List<NameValue> classStrength(List<SchoolClass> classes) {
        if (classes.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : schoolClassRepository.countStudentsByClassIds(classes.stream().map(SchoolClass::getId).toList())) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return classes.stream()
                .map(c -> new NameValue(c.getDisplayName(), counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /** Rows of [gender, count] -> "Male" / "Female" / "Other" in that order, only genders that occur. */
    static List<NameValue> genderSplit(List<Object[]> rows) {
        Map<Gender, Long> counts = new EnumMap<>(Gender.class);
        for (Object[] row : rows) {
            counts.put((Gender) row[0], ((Number) row[1]).longValue());
        }
        List<NameValue> split = new ArrayList<>();
        counts.forEach((gender, count) -> split.add(new NameValue(label(gender), count)));
        return split;
    }

    /**
     * Every student's overall letter in one exam, through the ACTIVE grading strategy (Strategy pattern, F18):
     * grade each subject, summarise the student, count the letters. One query loads all marks.
     */
    private List<NameValue> gradeDistribution(Long examId) {
        GradingStrategy strategy = gradingStrategyResolver.active();
        Map<Long, List<SubjectGrade>> gradesByStudent = new LinkedHashMap<>();
        for (Object[] row : dashboardRepository.findMarksOfExam(examId)) {
            Long studentId = ((Number) row[0]).longValue();
            SubjectGrade grade = strategy.gradeSubject(
                    (BigDecimal) row[1], (Integer) row[3], (Integer) row[4], (Boolean) row[2]);
            gradesByStudent.computeIfAbsent(studentId, id -> new ArrayList<>()).add(grade);
        }
        List<String> letters = gradesByStudent.values().stream()
                .map(grades -> strategy.summarize(grades).letter())
                .toList();
        return countLetters(letters);
    }

    /** Letters -> counts in the order A+ A A- B C D F (zeros included, so the bars never move). */
    static List<NameValue> countLetters(List<String> letters) {
        Map<String, Long> counts = new LinkedHashMap<>();
        LETTER_ORDER.forEach(letter -> counts.put(letter, 0L));
        letters.forEach(letter -> counts.merge(letter, 1L, Long::sum));
        List<NameValue> distribution = new ArrayList<>();
        counts.forEach((letter, count) -> distribution.add(new NameValue(letter, count)));
        return distribution;
    }

    private List<RecentPayment> recentPayments() {
        List<Payment> payments = dashboardRepository.findRecentPayments(PageRequest.of(0, RECENT_PAYMENTS));
        return payments.stream()
                .map(p -> new RecentPayment(
                        p.getInvoice().getStudent().getFullName(),
                        p.getInvoice().getInvoiceNo(),
                        p.getAmount(),
                        p.getMethod(),
                        p.getPaidAt()))
                .toList();
    }

    // ---------------------------------------------------------------- teacher

    @Transactional(readOnly = true)
    public TeacherDashboard teacherDashboard() {
        Teacher teacher = accessGuard.currentTeacher();
        TeachingLoad load = teacherService.myTeachingLoad();
        LocalDate today = LocalDate.now(clock);

        // Distinct classes of the CURRENT year: where the teacher is class teacher or teaches a subject.
        String currentYear = academicYearRepository.findByCurrentTrue().map(AcademicYear::getName).orElse(null);
        Map<Long, String> classes = distinctClasses(load, currentYear);

        List<ClassToday> classesToday = List.of();
        if (!classes.isEmpty()) {
            Map<Long, Long> students = new HashMap<>();
            for (Object[] row : schoolClassRepository.countStudentsByClassIds(classes.keySet())) {
                students.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
            Set<Long> taken = dashboardRepository.findClassIdsWithAttendanceOn(today, classes.keySet());
            classesToday = classes.entrySet().stream()
                    .map(c -> new ClassToday(
                            c.getKey(),
                            c.getValue(),
                            students.getOrDefault(c.getKey(), 0L),
                            taken.contains(c.getKey())))
                    .toList();
        }

        return new TeacherDashboard(teacher.getFullName(), load, classesToday, latestNotices());
    }

    /** classId -> class name, each class once, sorted by name ("Class 9 - A" before "Class 9 - B"). */
    static Map<Long, String> distinctClasses(TeachingLoad load, String currentYear) {
        Map<Long, String> classes = new LinkedHashMap<>();
        for (ClassRef ref : load.classTeacherOf()) {
            if (ref.academicYear().equals(currentYear)) {
                classes.putIfAbsent(ref.classId(), ref.className());
            }
        }
        for (SubjectAssignment subject : load.subjects()) {
            if (subject.academicYear().equals(currentYear)) {
                classes.putIfAbsent(subject.classId(), subject.className());
            }
        }
        Map<Long, String> sorted = new LinkedHashMap<>();
        classes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    // ---------------------------------------------------------------- shared

    /** The 5 notices the caller may see (pinned first), using the notice module's own audience rule. */
    private List<NoticeResponse> latestNotices() {
        return noticeService.list(PageRequest.of(0, NOTICES)).content();
    }

    private static String label(Gender gender) {
        String name = gender.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
