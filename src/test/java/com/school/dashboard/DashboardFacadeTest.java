package com.school.dashboard;

import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.SchoolClassRepository;
import com.school.academic.SubjectRepository;
import com.school.attendance.AttendanceStatus;
import com.school.common.PageResponse;
import com.school.dashboard.DashboardDtos.AdminDashboard;
import com.school.dashboard.DashboardDtos.MonthAmount;
import com.school.dashboard.DashboardDtos.NameValue;
import com.school.dashboard.DashboardDtos.TeacherDashboard;
import com.school.dashboard.DashboardDtos.TrendPoint;
import com.school.exam.Exam;
import com.school.fee.FeeDtos.FeeSummary;
import com.school.fee.FeeInvoiceService;
import com.school.grading.Gpa5GradingStrategy;
import com.school.grading.GradingStrategyResolver;
import com.school.notice.NoticeService;
import com.school.security.AccessGuard;
import com.school.student.Gender;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherDtos.ClassRef;
import com.school.teacher.TeacherDtos.SubjectAssignment;
import com.school.teacher.TeacherDtos.TeachingLoad;
import com.school.teacher.TeacherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardFacadeTest {

    /** 00:30 on 1 Nov 2026 in Dhaka = still 31 Oct in UTC. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T18:30:00Z"), ZoneId.of("Asia/Dhaka"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Mock
    private DashboardRepository dashboardRepository;
    @Mock
    private AcademicYearRepository academicYearRepository;
    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private FeeInvoiceService feeInvoiceService;
    @Mock
    private NoticeService noticeService;
    @Mock
    private TeacherService teacherService;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private GradingStrategyResolver gradingStrategyResolver;

    private DashboardFacade facade;

    @BeforeEach
    void setUp() {
        facade = new DashboardFacade(dashboardRepository, academicYearRepository, schoolClassRepository,
                subjectRepository, feeInvoiceService, noticeService, teacherService, accessGuard,
                gradingStrategyResolver, CLOCK);
        when(feeInvoiceService.summary()).thenReturn(FeeSummary.of(null, null, 0));
        when(noticeService.list(any())).thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0));
        when(gradingStrategyResolver.active()).thenReturn(new Gpa5GradingStrategy());
    }

    @Test
    void adminDashboard_emptyDatabase_givesZerosNullsAndSixZeroMonths() {
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());

        AdminDashboard dashboard = facade.adminDashboard();

        assertThat(dashboard.academicYear()).isNull();
        assertThat(dashboard.counts().students()).isZero();
        assertThat(dashboard.counts().classes()).isZero();
        assertThat(dashboard.todayAttendance()).isNull();
        assertThat(dashboard.attendanceTrend()).isEmpty();
        assertThat(dashboard.classStrength()).isEmpty();
        assertThat(dashboard.genderSplit()).isEmpty();
        assertThat(dashboard.latestExam()).isNull();
        assertThat(dashboard.gradeDistribution()).isEmpty();
        assertThat(dashboard.recentPayments()).isEmpty();
        // Months are taken in Dhaka: at 00:30 on 1 Nov it is already November there.
        assertThat(dashboard.monthlyCollection()).extracting(MonthAmount::month)
                .containsExactly("Jun 2026", "Jul 2026", "Aug 2026", "Sep 2026", "Oct 2026", "Nov 2026");
        assertThat(dashboard.monthlyCollection()).extracting(MonthAmount::amount)
                .allMatch(amount -> amount.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void adminDashboard_todayAttendance_isTodaysPointOfTheTrend() {
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        when(dashboardRepository.countAttendanceByDateAndStatus(TODAY.minusDays(13), TODAY)).thenReturn(List.of(
                new Object[] {TODAY, AttendanceStatus.PRESENT, 3L},
                new Object[] {TODAY, AttendanceStatus.ABSENT, 1L},
                new Object[] {TODAY.minusDays(1), AttendanceStatus.LATE, 2L}));

        AdminDashboard dashboard = facade.adminDashboard();

        assertThat(dashboard.todayAttendance()).isEqualByComparingTo("75.0");
        assertThat(dashboard.attendanceTrend()).extracting(TrendPoint::date)
                .containsExactly(TODAY.minusDays(1), TODAY);
    }

    @Test
    void attendanceTrend_onlyRecordedDays_oldestFirst_excusedNotCounted() {
        LocalDate d1 = LocalDate.of(2026, 11, 20);
        LocalDate d2 = LocalDate.of(2026, 11, 24);

        List<TrendPoint> trend = DashboardFacade.attendanceTrend(List.of(
                new Object[] {d2, AttendanceStatus.PRESENT, 1L},
                new Object[] {d2, AttendanceStatus.ABSENT, 2L},
                new Object[] {d1, AttendanceStatus.PRESENT, 2L},
                new Object[] {d1, AttendanceStatus.LATE, 1L},
                new Object[] {d1, AttendanceStatus.EXCUSED, 5L}));

        assertThat(trend).hasSize(2);
        assertThat(trend.get(0).date()).isEqualTo(d1);
        assertThat(trend.get(0).percentage()).isEqualByComparingTo("100.0");
        assertThat(trend.get(1).percentage()).isEqualByComparingTo("33.3");
    }

    @Test
    void fillMonths_keepsZeroMonthsAndScalesAmounts() {
        List<MonthAmount> months = DashboardFacade.fillMonths(YearMonth.of(2026, 9), List.of(
                new Object[] {"2026-10", new BigDecimal("1500")},
                new Object[] {"2027-01", new BigDecimal("250.5")}));

        assertThat(months).extracting(MonthAmount::month)
                .containsExactly("Sep 2026", "Oct 2026", "Nov 2026", "Dec 2026", "Jan 2027", "Feb 2027");
        assertThat(months).extracting(m -> m.amount().toPlainString())
                .containsExactly("0.00", "1500.00", "0.00", "0.00", "250.50", "0.00");
    }

    @Test
    void genderSplit_readableNamesInEnumOrder() {
        List<NameValue> split = DashboardFacade.genderSplit(List.of(
                new Object[] {Gender.OTHER, 1L},
                new Object[] {Gender.FEMALE, 4L}));

        assertThat(split).containsExactly(new NameValue("Female", 4), new NameValue("Other", 1));
    }

    @Test
    void gradeDistribution_usesActiveStrategy_perStudent_inLetterOrder() {
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        Exam exam = mock(Exam.class);
        when(exam.getId()).thenReturn(7L);
        when(exam.getName()).thenReturn("Half Yearly");
        when(dashboardRepository.findLatestPublishedExams(any())).thenReturn(List.of(exam));
        when(dashboardRepository.findMarksOfExam(7L)).thenReturn(List.of(
                // Student 1: 85 + 75 -> A+ and A -> GPA 4.50 -> A
                new Object[] {1L, new BigDecimal("85"), false, 100, 33},
                new Object[] {1L, new BigDecimal("75"), false, 100, 33},
                // Student 2: one subject below pass marks -> F overall
                new Object[] {2L, new BigDecimal("90"), false, 100, 33},
                new Object[] {2L, new BigDecimal("20"), false, 100, 33},
                // Student 3: absent in the only subject -> F
                new Object[] {3L, null, true, 100, 33}));

        AdminDashboard dashboard = facade.adminDashboard();

        assertThat(dashboard.latestExam()).isEqualTo("Half Yearly");
        assertThat(dashboard.gradeDistribution()).extracting(NameValue::name)
                .containsExactly("A+", "A", "A-", "B", "C", "D", "F");
        assertThat(dashboard.gradeDistribution()).extracting(NameValue::value)
                .containsExactly(0L, 1L, 0L, 0L, 0L, 0L, 2L);
    }

    @Test
    void distinctClasses_currentYearOnly_eachClassOnce_sortedByName() {
        TeachingLoad load = new TeachingLoad(
                List.of(new ClassRef(2L, "Class 9 - B", "2026"), new ClassRef(9L, "Class 8 - A", "2025")),
                List.of(
                        new SubjectAssignment(1L, "Class 9 - A", "2026", 10L, "MATH", "Mathematics"),
                        new SubjectAssignment(2L, "Class 9 - B", "2026", 11L, "ENG", "English")));

        Map<Long, String> classes = DashboardFacade.distinctClasses(load, "2026");

        assertThat(classes).containsExactly(Map.entry(1L, "Class 9 - A"), Map.entry(2L, "Class 9 - B"));
    }

    @Test
    void teacherDashboard_countsStudents_andMarksTakenClasses() {
        Teacher teacher = mock(Teacher.class);
        when(teacher.getFullName()).thenReturn("Ayesha Rahman");
        when(accessGuard.currentTeacher()).thenReturn(teacher);
        when(teacherService.myTeachingLoad()).thenReturn(new TeachingLoad(
                List.of(new ClassRef(1L, "Class 9 - A", "2026")),
                List.of(new SubjectAssignment(2L, "Class 9 - B", "2026", 10L, "MATH", "Mathematics"))));
        AcademicYear year = mock(AcademicYear.class);
        when(year.getName()).thenReturn("2026");
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.of(year));
        when(schoolClassRepository.countStudentsByClassIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[] {1L, 30L}));
        when(dashboardRepository.findClassIdsWithAttendanceOn(eq(TODAY), anyCollection())).thenReturn(Set.of(1L));

        TeacherDashboard dashboard = facade.teacherDashboard();

        assertThat(dashboard.teacherName()).isEqualTo("Ayesha Rahman");
        assertThat(dashboard.classesToday()).hasSize(2);
        assertThat(dashboard.classesToday().get(0).students()).isEqualTo(30);
        assertThat(dashboard.classesToday().get(0).attendanceTaken()).isTrue();
        assertThat(dashboard.classesToday().get(1).students()).isZero();
        assertThat(dashboard.classesToday().get(1).attendanceTaken()).isFalse();
    }

    @Test
    void teacherDashboard_noClasses_runsNoClassQueries() {
        Teacher teacher = mock(Teacher.class);
        when(accessGuard.currentTeacher()).thenReturn(teacher);
        when(teacherService.myTeachingLoad()).thenReturn(new TeachingLoad(List.of(), List.of()));
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());

        TeacherDashboard dashboard = facade.teacherDashboard();

        assertThat(dashboard.classesToday()).isEmpty();
        verifyNoInteractions(schoolClassRepository, dashboardRepository);
    }
}
