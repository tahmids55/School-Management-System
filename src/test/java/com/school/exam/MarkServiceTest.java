package com.school.exam;

import com.school.academic.AcademicYear;
import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.exam.ExamDtos.MarkEntry;
import com.school.exam.ExamDtos.MarkSheet;
import com.school.exam.ExamDtos.MarkSheetRequest;
import com.school.security.AccessGuard;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkServiceTest {

    @Mock
    private ExamRepository examRepository;
    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private ClassSubjectRepository classSubjectRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private MarkRepository markRepository;
    @Mock
    private AccessGuard accessGuard;
    @Mock
    private AuditService auditService;

    private MarkService markService;
    private AcademicYear year2026;
    private SchoolClass class9A;
    private Subject math;
    private Exam draftExam;
    private Student student1;
    private Student student2;

    @BeforeEach
    void setUp() {
        markService = new MarkService(
                examRepository,
                schoolClassRepository,
                classSubjectRepository,
                studentRepository,
                markRepository,
                accessGuard,
                auditService
        );

        year2026 = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        ReflectionTestUtils.setField(year2026, "id", 1L);

        class9A = new SchoolClass(year2026, "Class 9", "A", "101", 40);
        ReflectionTestUtils.setField(class9A, "id", 10L);

        math = new Subject("MATH", "Mathematics", 100, 33);
        ReflectionTestUtils.setField(math, "id", 20L);

        draftExam = new Exam(year2026, "Annual", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(draftExam, "id", 30L);

        student1 = studentWithId(101L, "ADM-1", "Rahim", "Uddin", 1);
        student2 = studentWithId(102L, "ADM-2", "Karim", "Mia", 2);
    }

    @Test
    void saveSheet_validatesAllRowsFirst_soOneBadRowSavesNothing() {
        stubValidContext(draftExam);
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(student1, student2));

        MarkSheetRequest request = new MarkSheetRequest(10L, 20L, List.of(
                new MarkEntry(101L, new BigDecimal("85"), false),
                new MarkEntry(102L, new BigDecimal("100.01"), false) // bad row!
        ));

        assertThatThrownBy(() -> markService.saveSheet(30L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Marks must be between 0 and 100 for MATH");

        verify(markRepository, never()).saveAllAndFlush(any());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void saveSheet_rejectsDuplicateStudentAndInactiveStudentAndPublishedExam() {
        stubValidContext(draftExam);
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(student1));

        // Duplicate student ID
        assertThatThrownBy(() -> markService.saveSheet(30L, new MarkSheetRequest(10L, 20L, List.of(
                new MarkEntry(101L, new BigDecimal("50"), false),
                new MarkEntry(101L, new BigDecimal("60"), false)
        ))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Duplicate student 101");

        // Student not active in class
        assertThatThrownBy(() -> markService.saveSheet(30L, new MarkSheetRequest(10L, 20L, List.of(
                new MarkEntry(999L, new BigDecimal("50"), false)
        ))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Student 999 is not an active student of this class");

        // Published exam -> 409
        draftExam.publish(Instant.parse("2026-11-20T00:00:00Z"));
        assertThatThrownBy(() -> markService.saveSheet(30L, new MarkSheetRequest(10L, 20L, List.of(
                new MarkEntry(101L, new BigDecimal("50"), false)
        ))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Results of Annual are published. Unpublish them before making changes.");
    }

    @Test
    void getSheet_rejectsYearMismatchAndSubjectNotInClass() {
        AcademicYear year2025 = new AcademicYear("2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        ReflectionTestUtils.setField(year2025, "id", 2L);
        SchoolClass oldClass = new SchoolClass(year2025, "Class 8", "A", "102", 40);
        ReflectionTestUtils.setField(oldClass, "id", 11L);

        when(examRepository.findByIdWithAcademicYear(30L)).thenReturn(Optional.of(draftExam));
        when(schoolClassRepository.findById(11L)).thenReturn(Optional.of(oldClass));

        assertThatThrownBy(() -> markService.getSheet(30L, 11L, 20L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong to academic year 2026");

        when(schoolClassRepository.findById(10L)).thenReturn(Optional.of(class9A));
        when(classSubjectRepository.findBySchoolClassIdAndSubjectIdWithDetails(10L, 20L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> markService.getSheet(30L, 10L, 20L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("This subject is not taught in the selected class");
    }

    @Test
    void saveSheet_validRows_savesAllAndAudits() {
        stubValidContext(draftExam);
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(student1, student2));
        when(markRepository.findByExamIdAndSubjectIdAndStudentIdIn(30L, 20L, List.of(101L, 102L)))
                .thenReturn(List.of());

        MarkSheet sheet = markService.saveSheet(30L, new MarkSheetRequest(10L, 20L, List.of(
                new MarkEntry(101L, new BigDecimal("88.50"), false),
                new MarkEntry(102L, null, true)
        )));

        assertThat(sheet.editable()).isTrue();
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0).marksObtained()).isEqualByComparingTo("88.50");
        assertThat(sheet.rows().get(1).absent()).isTrue();
        assertThat(sheet.rows().get(1).marksObtained()).isNull();
        verify(accessGuard).checkCanGradeSubject(10L, 20L);
        verify(markRepository).saveAllAndFlush(any());
        verify(auditService).record(eq(MarkService.AUDIT_MARKS_SAVED), eq("Exam"), eq("30"), anyString());
    }

    private void stubValidContext(Exam exam) {
        when(examRepository.findByIdWithAcademicYear(30L)).thenReturn(Optional.of(exam));
        when(schoolClassRepository.findById(10L)).thenReturn(Optional.of(class9A));
        ClassSubject cs = new ClassSubject(class9A, math, null);
        when(classSubjectRepository.findBySchoolClassIdAndSubjectIdWithDetails(10L, 20L))
                .thenReturn(Optional.of(cs));
    }

    private Student studentWithId(Long id, String admNo, String first, String last, int roll) {
        Student s = new Student(admNo);
        ReflectionTestUtils.setField(s, "id", id);
        s.setFirstName(first);
        s.setLastName(last);
        s.setRollNo(roll);
        return s;
    }
}
