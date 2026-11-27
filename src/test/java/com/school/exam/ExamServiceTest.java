package com.school.exam;

import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.exam.ExamDtos.ExamRequest;
import com.school.exam.ExamDtos.ExamResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class ExamServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2026, 12, 31);

    @Mock
    private ExamRepository examRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AuditService auditService;

    private ExamService examService;
    private AcademicYear year2026;

    @BeforeEach
    void setUp() {
        examService = new ExamService(examRepository, academicYearRepository, auditService);
        year2026 = new AcademicYear("2026", JAN_1, DEC_31);
        ReflectionTestUtils.setField(year2026, "id", 1L);
        year2026.setCurrent(true);
    }

    @Test
    void examEntity_publishUnpublishAndRequireDraft() {
        Exam exam = new Exam(year2026, "Midterm", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10));
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
        assertThat(exam.getPublishedAt()).isNull();
        exam.requireDraft();

        Instant publishedAt = Instant.parse("2026-06-20T10:00:00Z");
        exam.publish(publishedAt);
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        assertThat(exam.getPublishedAt()).isEqualTo(publishedAt);

        assertThatThrownBy(exam::requireDraft)
                .isInstanceOf(ConflictException.class)
                .hasMessage("Results of Midterm are published. Unpublish them before making changes.");

        exam.unpublish();
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
        assertThat(exam.getPublishedAt()).isNull();
        exam.requireDraft();
    }

    @Test
    void create_trimsName_allowsSameDayEnd_andAudits() {
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(year2026));
        when(examRepository.existsByAcademicYearIdAndNameIgnoreCase(1L, "Annual Examination")).thenReturn(false);
        when(examRepository.saveAndFlush(any(Exam.class))).thenAnswer(inv -> {
            Exam saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        LocalDate day = LocalDate.of(2026, 11, 15);
        ExamResponse response = examService.create(new ExamRequest(1L, "  Annual Examination  ", day, day));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Annual Examination");
        assertThat(response.academicYearName()).isEqualTo("2026");
        assertThat(response.status()).isEqualTo(ExamStatus.DRAFT);
        verify(auditService).record(eq(ExamService.AUDIT_EXAM_CREATED), eq("Exam"), eq("10"), anyString());
    }

    @Test
    void create_dateBoundariesAndDuplicateAndMissingYear() {
        when(academicYearRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> examService.create(new ExamRequest(99L, "Midterm", JAN_1, JAN_1)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Academic year 99 was not found");

        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(year2026));

        assertThatThrownBy(() -> examService.create(new ExamRequest(1L, "Midterm", LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 9))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("End date cannot be before start date");

        assertThatThrownBy(() -> examService.create(new ExamRequest(1L, "Midterm", LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 5))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Exam dates must be inside academic year 2026");

        assertThatThrownBy(() -> examService.create(new ExamRequest(1L, "Midterm", LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Exam dates must be inside academic year 2026");

        when(examRepository.existsByAcademicYearIdAndNameIgnoreCase(1L, "Midterm")).thenReturn(true);
        assertThatThrownBy(() -> examService.create(new ExamRequest(1L, " Midterm ", JAN_1, DEC_31)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An exam named Midterm already exists in 2026");
    }

    @Test
    void updateAndDelete_rejectPublishedExamWith409_andDeleteDraftAudits() {
        Exam published = new Exam(year2026, "Final", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(published, "id", 5L);
        published.publish(Instant.parse("2026-11-20T00:00:00Z"));
        when(examRepository.findByIdWithAcademicYear(5L)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> examService.update(5L, new ExamRequest(1L, "Final 2", JAN_1, JAN_1)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Results of Final are published. Unpublish them before making changes.");

        assertThatThrownBy(() -> examService.delete(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Results of Final are published. Unpublish them before making changes.");
        verify(examRepository, never()).delete(any());

        Exam draft = new Exam(year2026, "Quiz", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2));
        ReflectionTestUtils.setField(draft, "id", 6L);
        when(examRepository.findByIdWithAcademicYear(6L)).thenReturn(Optional.of(draft));

        examService.delete(6L);
        verify(examRepository).delete(draft);
        verify(auditService).record(eq(ExamService.AUDIT_EXAM_DELETED), eq("Exam"), eq("6"), anyString());
    }

    @Test
    void list_whenNoCurrentYear_returnsEmptyList() {
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        assertThat(examService.list(null)).isEmpty();
    }
}
