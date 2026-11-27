package com.school.exam;

import com.school.academic.AcademicYear;
import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.NotFoundException;
import com.school.exam.ExamDtos.ExamResponse;
import com.school.grading.Gpa5GradingStrategy;
import com.school.grading.GradingStrategyResolver;
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
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-27T08:00:00Z");

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
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ResultService resultService;
    private AcademicYear year2026;
    private SchoolClass class9A;
    private Subject math;
    private Exam exam;
    private Student student1;

    @BeforeEach
    void setUp() {
        GradingStrategyResolver resolver = new GradingStrategyResolver(List.of(new Gpa5GradingStrategy()), "GPA_5");
        resultService = new ResultService(
                examRepository,
                schoolClassRepository,
                classSubjectRepository,
                studentRepository,
                markRepository,
                resolver,
                new ResultCalculator(),
                accessGuard,
                studentAccessGuard,
                auditService,
                eventPublisher,
                Clock.fixed(FIXED_NOW, ZoneId.of("Asia/Dhaka"))
        );

        year2026 = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        ReflectionTestUtils.setField(year2026, "id", 1L);

        class9A = new SchoolClass(year2026, "Class 9", "A", "101", 40);
        ReflectionTestUtils.setField(class9A, "id", 10L);

        math = new Subject("MATH", "Mathematics", 100, 33);
        ReflectionTestUtils.setField(math, "id", 20L);

        exam = new Exam(year2026, "Annual", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(exam, "id", 30L);

        student1 = new Student("ADM-1");
        ReflectionTestUtils.setField(student1, "id", 101L);
        student1.setFirstName("Amina");
        student1.setLastName("Islam");
        student1.setRollNo(1);
        student1.setSchoolClass(class9A);
    }

    @Test
    void publish_whenNothingToPublish_throws422() {
        when(examRepository.findByIdWithAcademicYear(30L)).thenReturn(Optional.of(exam));
        when(schoolClassRepository.findAllByYearWithYear(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> resultService.publish(30L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no active students with assigned subjects");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publish_whenMarksMissing_throws422ListingMissingClassSubject() {
        when(examRepository.findByIdWithAcademicYear(30L)).thenReturn(Optional.of(exam));
        when(schoolClassRepository.findAllByYearWithYear(1L)).thenReturn(List.of(class9A));
        when(classSubjectRepository.findAllByClassIdWithDetails(10L)).thenReturn(List.of(new ClassSubject(class9A, math, null)));
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(student1));
        when(markRepository.findByExamIdAndStudentIdIn(30L, List.of(101L))).thenReturn(List.of());

        assertThatThrownBy(() -> resultService.publish(30L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Class 9 - A / MATH");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publish_whenComplete_setsPublishedAt_auditsAndPublishesEvent_andUnpublishReverts() {
        when(examRepository.findByIdWithAcademicYear(30L)).thenReturn(Optional.of(exam));
        when(schoolClassRepository.findAllByYearWithYear(1L)).thenReturn(List.of(class9A));
        when(classSubjectRepository.findAllByClassIdWithDetails(10L)).thenReturn(List.of(new ClassSubject(class9A, math, null)));
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(student1));
        when(markRepository.findByExamIdAndStudentIdIn(30L, List.of(101L)))
                .thenReturn(List.of(new Mark(exam, student1, math, new BigDecimal("85"), false)));
        when(examRepository.saveAndFlush(exam)).thenReturn(exam);

        ExamResponse published = resultService.publish(30L);

        assertThat(published.status()).isEqualTo(ExamStatus.PUBLISHED);
        assertThat(published.publishedAt()).isEqualTo(FIXED_NOW);
        verify(auditService).record(eq(ResultService.AUDIT_RESULTS_PUBLISHED), eq("Exam"), eq("30"), anyString());

        ArgumentCaptor<ResultsPublishedEvent> eventCaptor = ArgumentCaptor.forClass(ResultsPublishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new ResultsPublishedEvent(30L, "Annual"));

        ExamResponse unpublished = resultService.unpublish(30L);
        assertThat(unpublished.status()).isEqualTo(ExamStatus.DRAFT);
        assertThat(unpublished.publishedAt()).isNull();
        verify(auditService).record(eq(ResultService.AUDIT_RESULTS_UNPUBLISHED), eq("Exam"), eq("30"), anyString());
    }

    @Test
    void myExamResult_whenUnpublished_throws404WithExactMessage() {
        when(studentAccessGuard.currentStudent()).thenReturn(student1);
        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(class9A));
        when(examRepository.findByIdWithAcademicYear(30L)).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> resultService.myExamResult(30L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No published result found for this exam");
    }
}
