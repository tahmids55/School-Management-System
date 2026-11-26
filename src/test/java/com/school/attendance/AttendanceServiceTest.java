package com.school.attendance;

import com.school.academic.AcademicYear;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.attendance.AttendanceDtos.AttendanceSheet;
import com.school.attendance.AttendanceDtos.Entry;
import com.school.attendance.AttendanceDtos.SaveSheetRequest;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.NotFoundException;
import com.school.security.AccessGuard;
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
    private AuditService auditService;

    private AttendanceService service;
    private SchoolClass class9A;
    private Student rahim;
    private Student karim;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(
                schoolClassRepository, studentRepository, attendanceRecordRepository,
                accessGuard, auditService, DHAKA_0030);

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
