package com.school.attendance;

import com.school.academic.AcademicYear;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.attendance.AttendanceDtos.AttendanceSheet;
import com.school.attendance.AttendanceDtos.ClassReport;
import com.school.attendance.AttendanceDtos.Entry;
import com.school.attendance.AttendanceDtos.SaveSheetRequest;
import com.school.attendance.AttendanceDtos.StudentAttendance;
import com.school.attendance.AttendanceDtos.StudentSummary;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ForbiddenException;
import com.school.common.exception.NotFoundException;
import com.school.security.AccessGuard;
import com.school.security.StudentAccessGuard;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    /**
     * 00:30 on 26 Nov 2026 in Dhaka = 18:30 on 25 Nov in UTC.
     * The old prototype took "today" from the server's zone and rejected 26 Nov here.
     */
    private static final Clock DHAKA_0030 =
            Clock.fixed(Instant.parse("2026-11-25T18:30:00Z"), ZoneId.of("Asia/Dhaka"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private AuditService auditService;

    private AttendanceService service;
    private SchoolClass class9A;
    private Student rahim;
    private Student karim;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(
                schoolClassRepository, studentRepository, attendanceRecordRepository,
                accessGuard, studentAccessGuard, auditService, DHAKA_0030, 75);

        AcademicYear year2026 = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        class9A = classWithId(10L, year2026);
        rahim = studentWithId(101L, "ADM-1", "Rahim", 1);
        karim = studentWithId(102L, "ADM-2", "Karim", 2);
    }

    @Test
    void saveSheet_todayInDhakaAt0030_isAccepted_whileUtcIsStillYesterday() {
        stubClassAndRoster(class9A);

        AttendanceSheet sheet = service.saveSheet(new SaveSheetRequest(10L, TODAY, List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null),
                new Entry(102L, AttendanceStatus.LATE, "  Bus was late  "))));

        assertThat(sheet.rows()).extracting("status")
                .containsExactly(AttendanceStatus.PRESENT, AttendanceStatus.LATE);
        assertThat(sheet.rows().get(1).remarks()).isEqualTo("Bus was late");
        verify(attendanceRecordRepository).saveAllAndFlush(any());
        verify(auditService).record(eq("ATTENDANCE_SAVED"), eq("SchoolClass"), eq("10"), anyString());
    }

    @Test
    void saveSheet_tomorrowInDhaka_isRejected_andNothingSaved() {
        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(class9A));

        assertThatThrownBy(() -> service.saveSheet(new SaveSheetRequest(10L, TODAY.plusDays(1), List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Attendance cannot be taken for a future date");
        verify(attendanceRecordRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void saveSheet_yearBoundaries_areInclusive() {
        AcademicYear year2025 = new AcademicYear("2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        SchoolClass class2025 = classWithId(10L, year2025);
        stubClassAndRoster(class2025);

        // First and last day of the year are inside.
        service.saveSheet(new SaveSheetRequest(10L, LocalDate.of(2025, 1, 1), List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null))));
        service.saveSheet(new SaveSheetRequest(10L, LocalDate.of(2025, 12, 31), List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null))));

        // One day before and one day after are outside.
        assertThatThrownBy(() -> service.saveSheet(new SaveSheetRequest(10L, LocalDate.of(2024, 12, 31), List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The date is outside academic year 2025");
        assertThatThrownBy(() -> service.saveSheet(new SaveSheetRequest(10L, LocalDate.of(2026, 1, 1), List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The date is outside academic year 2025");
    }

    @Test
    void saveSheet_oneBadRow_savesNothing() {
        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(class9A));
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(rahim, karim));

        assertThatThrownBy(() -> service.saveSheet(new SaveSheetRequest(10L, TODAY, List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null),
                new Entry(999L, AttendanceStatus.ABSENT, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Student 999 is not an active student of this class");

        verify(attendanceRecordRepository, never()).saveAllAndFlush(any());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void saveSheet_duplicateStudent_isRejected() {
        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(class9A));
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(rahim, karim));

        assertThatThrownBy(() -> service.saveSheet(new SaveSheetRequest(10L, TODAY, List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null),
                new Entry(101L, AttendanceStatus.ABSENT, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Duplicate student 101 in attendance sheet");
        verify(attendanceRecordRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveSheet_again_correctsTheExistingRecord_insteadOfAddingOne() {
        stubClassAndRoster(class9A);
        AttendanceRecord saved = new AttendanceRecord(rahim, class9A, TODAY, AttendanceStatus.ABSENT, "Sick");
        when(attendanceRecordRepository.findByDateAndStudentIds(eq(TODAY), anyCollection()))
                .thenReturn(List.of(saved));

        service.saveSheet(new SaveSheetRequest(10L, TODAY, List.of(
                new Entry(101L, AttendanceStatus.PRESENT, "  "))));

        ArgumentCaptor<List<AttendanceRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRecordRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).containsExactly(saved);
        assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(saved.getRemarks()).isNull();
    }

    @Test
    void saveSheet_teacherOfAnotherClass_isDenied_beforeAnythingIsRead() {
        doThrow(new AccessDeniedException(AccessGuard.NOT_TEACHING_CLASS_MESSAGE))
                .when(accessGuard).checkCanManageClass(10L);

        assertThatThrownBy(() -> service.saveSheet(new SaveSheetRequest(10L, TODAY, List.of(
                new Entry(101L, AttendanceStatus.PRESENT, null)))))
                .isInstanceOf(AccessDeniedException.class);
        verify(studentRepository, never()).findActiveRoster(any());
        verify(attendanceRecordRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void getSheet_unknownClass_is404() {
        doThrow(new NotFoundException("Class", 77L)).when(accessGuard).checkCanManageClass(77L);

        assertThatThrownBy(() -> service.getSheet(77L, TODAY))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Class 77 was not found");
    }

    @Test
    void getSheet_notTakenYet_hasNullStatuses() {
        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(class9A));
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(rahim, karim));

        AttendanceSheet sheet = service.getSheet(10L, TODAY);

        assertThat(sheet.taken()).isFalse();
        assertThat(sheet.className()).isEqualTo("Class 9 - A");
        assertThat(sheet.rows()).extracting("studentId").containsExactly(101L, 102L);
        assertThat(sheet.rows()).extracting("status").containsOnlyNulls();
    }

    // ---------- Reports (F20) ----------

    @Test
    void classReport_usesGroupedCounts_flagsLow_andIgnoresExcused() {
        stubClassAndRoster(class9A);
        LocalDate from = LocalDate.of(2026, 11, 1);
        when(attendanceRecordRepository.countByStudentAndStatus(10L, from, TODAY)).thenReturn(List.of(
                // Rahim: 3 present + 1 absent = 75.0 (not low), 5 excused days ignored.
                new StatusCount(101L, AttendanceStatus.PRESENT, 3L),
                new StatusCount(101L, AttendanceStatus.ABSENT, 1L),
                new StatusCount(101L, AttendanceStatus.EXCUSED, 5L),
                // Karim: 1 late + 2 absent = 33.3 (low).
                new StatusCount(102L, AttendanceStatus.LATE, 1L),
                new StatusCount(102L, AttendanceStatus.ABSENT, 2L)));
        when(attendanceRecordRepository.countDaysTaken(10L, from, TODAY)).thenReturn(9L);

        ClassReport report = service.classReport(10L, from, TODAY);

        assertThat(report.daysTaken()).isEqualTo(9);
        assertThat(report.lowThreshold()).isEqualTo(75);
        StudentAttendance rahimLine = report.students().get(0);
        assertThat(rahimLine.percentage()).isEqualByComparingTo("75.0");
        assertThat(rahimLine.excused()).isEqualTo(5);
        assertThat(rahimLine.low()).isFalse();
        StudentAttendance karimLine = report.students().get(1);
        assertThat(karimLine.percentage()).isEqualByComparingTo("33.3");
        assertThat(karimLine.low()).isTrue();
    }

    @Test
    void classReport_studentWithoutRecords_is100_andNotLow() {
        stubClassAndRoster(class9A);

        ClassReport report = service.classReport(10L, TODAY, TODAY);

        assertThat(report.students()).extracting("percentage")
                .allSatisfy(p -> assertThat((BigDecimal) p).isEqualByComparingTo("100.0"));
        assertThat(report.students()).extracting("low").containsOnly(false);
    }

    @Test
    void classReport_toBeforeFrom_is422() {
        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(class9A));

        assertThatThrownBy(() -> service.classReport(10L, TODAY, TODAY.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("'to' date cannot be before 'from' date");
        verify(attendanceRecordRepository, never()).countByStudentAndStatus(any(), any(), any());
    }

    @Test
    void classReport_teacherOfAnotherClass_isDenied() {
        doThrow(new AccessDeniedException(AccessGuard.NOT_TEACHING_CLASS_MESSAGE))
                .when(accessGuard).checkCanManageClass(10L);

        assertThatThrownBy(() -> service.classReport(10L, TODAY, TODAY))
                .isInstanceOf(AccessDeniedException.class);
        verify(attendanceRecordRepository, never()).countByStudentAndStatus(any(), any(), any());
    }

    @Test
    void studentSummary_countsDays_newestFirst() {
        when(studentRepository.findById(101L)).thenReturn(Optional.of(rahim));
        LocalDate from = LocalDate.of(2026, 1, 1);
        when(attendanceRecordRepository.findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                101L, from, TODAY)).thenReturn(List.of(
                new AttendanceRecord(rahim, class9A, TODAY, AttendanceStatus.LATE, "Bus"),
                new AttendanceRecord(rahim, class9A, TODAY.minusDays(1), AttendanceStatus.ABSENT, null),
                new AttendanceRecord(rahim, class9A, TODAY.minusDays(2), AttendanceStatus.EXCUSED, null),
                new AttendanceRecord(rahim, class9A, TODAY.minusDays(3), AttendanceStatus.PRESENT, null)));

        StudentSummary summary = service.studentSummary(101L, from, TODAY);

        verify(studentAccessGuard).checkCanViewStudent(101L);
        assertThat(summary.present()).isEqualTo(1);
        assertThat(summary.late()).isEqualTo(1);
        assertThat(summary.absent()).isEqualTo(1);
        assertThat(summary.excused()).isEqualTo(1);
        assertThat(summary.percentage()).isEqualByComparingTo("66.7");
        assertThat(summary.low()).isTrue();
        assertThat(summary.records()).extracting("date")
                .containsExactly(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3));
        assertThat(summary.records().get(0).remarks()).isEqualTo("Bus");
    }

    @Test
    void studentSummary_unrelatedTeacher_isDenied_beforeAnythingIsRead() {
        doThrow(new ForbiddenException(StudentAccessGuard.OWN_CLASSES_ONLY))
                .when(studentAccessGuard).checkCanViewStudent(101L);

        assertThatThrownBy(() -> service.studentSummary(101L, TODAY, TODAY))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only view students of your own classes");
        verify(studentRepository, never()).findById(any());
        verify(attendanceRecordRepository, never())
                .findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(any(), any(), any());
    }

    @Test
    void studentSummary_unknownStudent_is404() {
        when(studentRepository.findById(555L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.studentSummary(555L, TODAY, TODAY))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Student 555 was not found");
    }

    @Test
    void studentSummary_toBeforeFrom_is422() {
        assertThatThrownBy(() -> service.studentSummary(101L, TODAY, TODAY.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("'to' date cannot be before 'from' date");
    }

    private void stubClassAndRoster(SchoolClass schoolClass) {
        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(schoolClass));
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(rahim, karim));
    }

    private static SchoolClass classWithId(Long id, AcademicYear year) {
        SchoolClass schoolClass = new SchoolClass(year, "Class 9", "A", "101", 40);
        ReflectionTestUtils.setField(schoolClass, "id", id);
        return schoolClass;
    }

    private static Student studentWithId(Long id, String admissionNo, String firstName, int roll) {
        Student student = new Student(admissionNo);
        ReflectionTestUtils.setField(student, "id", id);
        student.setFirstName(firstName);
        student.setLastName("Uddin");
        student.setRollNo(roll);
        return student;
    }
}
