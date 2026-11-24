package com.school.academic;

import com.school.academic.AcademicDtos.AcademicYearRequest;
import com.school.academic.AcademicDtos.AcademicYearResponse;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicYearServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2026, 12, 31);

    @Mock
    private AcademicYearRepository repository;

    @Mock
    private SchoolClassRepository classRepository;

    @Mock
    private AuditService auditService;

    private AcademicYearService service;

    @BeforeEach
    void setUp() {
        service = new AcademicYearService(repository, classRepository, auditService);
    }

    private static AcademicYear year(long id, String name, boolean current) {
        AcademicYear year = new AcademicYear(name, JAN_1, DEC_31);
        ReflectionTestUtils.setField(year, "id", id);
        year.setCurrent(current);
        return year;
    }

    private void saveReturnsArgumentWithId() {
        when(repository.save(any(AcademicYear.class))).thenAnswer(invocation -> {
            AcademicYear saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
    }

    @Test
    void contains_isInclusiveAtBothEnds() {
        AcademicYear year = year(1, "2026", false);
        assertThat(year.contains(JAN_1)).isTrue();
        assertThat(year.contains(DEC_31)).isTrue();
        assertThat(year.contains(LocalDate.of(2026, 6, 15))).isTrue();
        assertThat(year.contains(JAN_1.minusDays(1))).isFalse();
        assertThat(year.contains(DEC_31.plusDays(1))).isFalse();
        assertThat(year.contains(null)).isFalse();
    }

    @Test
    void list_returnsRepositoryOrderNewestFirst() {
        when(repository.findAllByOrderByStartDateDescIdDesc())
                .thenReturn(List.of(year(2, "2027", false), year(1, "2026", true)));

        assertThat(service.list()).extracting(AcademicYearResponse::name).containsExactly("2027", "2026");
    }

    @Test
    void create_firstYear_isTrimmedBecomesCurrentAndIsAudited() {
        when(repository.existsByNameIgnoreCase("2026")).thenReturn(false);
        when(repository.existsByCurrentTrue()).thenReturn(false);
        saveReturnsArgumentWithId();

        AcademicYearResponse response = service.create(new AcademicYearRequest("  2026 ", JAN_1, DEC_31));

        assertThat(response.name()).isEqualTo("2026");
        assertThat(response.current()).isTrue();
        verify(auditService).record(eq("ACADEMIC_YEAR_CREATED"), eq("AcademicYear"), eq("7"), anyString());
    }

    @Test
    void create_whenACurrentYearExists_isNotCurrent() {
        when(repository.existsByNameIgnoreCase("2027")).thenReturn(false);
        when(repository.existsByCurrentTrue()).thenReturn(true);
        saveReturnsArgumentWithId();

        AcademicYearResponse response = service.create(
                new AcademicYearRequest("2027", JAN_1.plusYears(1), DEC_31.plusYears(1)));

        assertThat(response.current()).isFalse();
    }

    @Test
    void create_duplicateNameIgnoringCase_is409AndNotSaved() {
        when(repository.existsByNameIgnoreCase("Session-A")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AcademicYearRequest(" Session-A ", JAN_1, DEC_31)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Academic year Session-A already exists");
        verify(repository, never()).save(any());
    }

    @Test
    void create_endEqualToStart_is422() {
        assertThatThrownBy(() -> service.create(new AcademicYearRequest("2026", JAN_1, JAN_1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("End date must be after start date");
        verify(repository, never()).save(any());
    }

    @Test
    void create_endBeforeStart_is422() {
        assertThatThrownBy(() -> service.create(new AcademicYearRequest("2026", DEC_31, JAN_1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("End date must be after start date");
    }

    @Test
    void create_endOneDayAfterStart_isAccepted() {
        when(repository.existsByNameIgnoreCase("Short")).thenReturn(false);
        when(repository.existsByCurrentTrue()).thenReturn(true);
        saveReturnsArgumentWithId();

        AcademicYearResponse response = service.create(new AcademicYearRequest("Short", JAN_1, JAN_1.plusDays(1)));

        assertThat(response.endDate()).isEqualTo(JAN_1.plusDays(1));
    }

    @Test
    void update_changesFields_andAllowsKeepingItsOwnName() {
        AcademicYear existing = year(3, "2026", false);
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCaseAndIdNot("2026", 3L)).thenReturn(false);
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        AcademicYearResponse response = service.update(3L,
                new AcademicYearRequest("2026", JAN_1.plusMonths(1), DEC_31));

        assertThat(response.startDate()).isEqualTo(JAN_1.plusMonths(1));
    }

    @Test
    void update_nameOfAnotherYear_is409() {
        when(repository.findById(3L)).thenReturn(Optional.of(year(3, "2026", false)));
        when(repository.existsByNameIgnoreCaseAndIdNot("2025", 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(3L, new AcademicYearRequest("2025", JAN_1, DEC_31)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Academic year 2025 already exists");
    }

    @Test
    void update_unknownId_is404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new AcademicYearRequest("2026", JAN_1, DEC_31)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Academic year 99 was not found");
    }

    @Test
    void activate_clearsOthersBeforeSettingThisOne_andAudits() {
        AcademicYear target = year(4, "2027", false);
        when(repository.findById(4L)).thenReturn(Optional.of(target));
        when(repository.saveAndFlush(target)).thenReturn(target);

        AcademicYearResponse response = service.activate(4L);

        assertThat(response.current()).isTrue();
        InOrder order = inOrder(repository);
        order.verify(repository).clearCurrent();
        order.verify(repository).saveAndFlush(target);
        verify(auditService).record(eq("ACADEMIC_YEAR_ACTIVATED"), eq("AcademicYear"), eq("4"), anyString());
    }

    @Test
    void activate_alreadyCurrent_changesNothing() {
        when(repository.findById(4L)).thenReturn(Optional.of(year(4, "2027", true)));

        assertThat(service.activate(4L).current()).isTrue();
        verify(repository, never()).clearCurrent();
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void delete_currentYear_is409() {
        when(repository.findById(1L)).thenReturn(Optional.of(year(1, "2026", true)));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The current academic year cannot be deleted");
        verify(repository, never()).delete(any());
    }

    @Test
    void delete_yearWithClasses_is409() {
        when(repository.findById(2L)).thenReturn(Optional.of(year(2, "2025", false)));
        when(classRepository.existsByAcademicYearId(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This academic year has classes or exams and cannot be deleted");
        verify(repository, never()).delete(any());
    }

    @Test
    void delete_yearWithExams_is409() {
        when(repository.findById(2L)).thenReturn(Optional.of(year(2, "2025", false)));
        when(classRepository.existsByAcademicYearId(2L)).thenReturn(false);
        when(repository.hasExams(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This academic year has classes or exams and cannot be deleted");
        verify(repository, never()).delete(any());
    }

    @Test
    void delete_unusedOldYear_isDeleted() {
        AcademicYear old = year(2, "2025", false);
        when(repository.findById(2L)).thenReturn(Optional.of(old));
        when(classRepository.existsByAcademicYearId(2L)).thenReturn(false);
        when(repository.hasExams(2L)).thenReturn(false);

        service.delete(2L);

        verify(repository).delete(old);
    }

    @Test
    void getCurrent_whenNoneSet_is404() {
        when(repository.findByCurrentTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent())
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No current academic year is set");
    }

    @Test
    void get_unknownId_is404() {
        when(repository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(5L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Academic year 5 was not found");
    }
}
