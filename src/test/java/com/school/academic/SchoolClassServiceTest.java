package com.school.academic;

import com.school.academic.AcademicDtos.ClassRequest;
import com.school.academic.AcademicDtos.ClassResponse;
import com.school.audit.AuditService;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolClassServiceTest {

    @Mock
    private SchoolClassRepository classRepository;

    @Mock
    private AcademicYearRepository yearRepository;

    @Mock
    private AuditService auditService;

    private SchoolClassService service;

    private AcademicYear year2026;

    @BeforeEach
    void setUp() {
        service = new SchoolClassService(classRepository, yearRepository, auditService);
        year2026 = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        ReflectionTestUtils.setField(year2026, "id", 1L);
    }

    private SchoolClass schoolClass(long id, String name, String section) {
        SchoolClass schoolClass = new SchoolClass(year2026, name, section, "R-201", 40);
        ReflectionTestUtils.setField(schoolClass, "id", id);
        return schoolClass;
    }

    private static ClassRequest request(String name, String section, String room, int capacity) {
        return new ClassRequest(1L, name, section, room, capacity, null);
    }

    @Test
    void list_withoutYear_andNoCurrentYear_isEmpty() {
        when(yearRepository.findByCurrentTrue()).thenReturn(Optional.empty());

        assertThat(service.list(null)).isEmpty();
        verifyNoInteractions(classRepository);
    }

    @Test
    void list_withoutYear_usesCurrentYear_andMapsStudentCounts() {
        when(yearRepository.findByCurrentTrue()).thenReturn(Optional.of(year2026));
        when(classRepository.findAllByYearWithYear(1L))
                .thenReturn(List.of(schoolClass(10, "Class 6", "A"), schoolClass(11, "Class 6", "B")));
        when(classRepository.countStudentsByClassIds(List.of(10L, 11L)))
                .thenReturn(List.<Object[]>of(new Object[] {10L, 25L}));

        List<ClassResponse> result = service.list(null);

        assertThat(result).extracting(ClassResponse::displayName).containsExactly("Class 6 - A", "Class 6 - B");
        assertThat(result).extracting(ClassResponse::studentCount).containsExactly(25L, 0L);
        assertThat(result.get(0).academicYearName()).isEqualTo("2026");
        assertThat(result.get(0).classTeacherId()).isNull();
    }

    @Test
    void list_chosenYearWithoutClasses_isEmpty_andSkipsCountQuery() {
        when(yearRepository.findById(1L)).thenReturn(Optional.of(year2026));
        when(classRepository.findAllByYearWithYear(1L)).thenReturn(List.of());

        assertThat(service.list(1L)).isEmpty();
        verify(classRepository, never()).countStudentsByClassIds(any());
    }

    @Test
    void list_unknownYear_is404() {
        when(yearRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Academic year 9 was not found");
    }

    @Test
    void create_trimsName_upperCasesSection_blankRoomIsNull_andAudits() {
        when(yearRepository.findById(1L)).thenReturn(Optional.of(year2026));
        when(classRepository.existsByAcademicYearIdAndNameIgnoreCaseAndSection(1L, "Class 6", "A")).thenReturn(false);
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(invocation -> {
            SchoolClass saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        ClassResponse response = service.create(request("  Class 6 ", " a ", "  ", 40));

        assertThat(response.name()).isEqualTo("Class 6");
        assertThat(response.section()).isEqualTo("A");
        assertThat(response.displayName()).isEqualTo("Class 6 - A");
        assertThat(response.room()).isNull();
        assertThat(response.studentCount()).isZero();
        verify(auditService).record(eq("CLASS_CREATED"), eq("SchoolClass"), eq("5"), anyString());
    }

    @Test
    void create_duplicateNameAndSectionInYear_is409() {
        when(yearRepository.findById(1L)).thenReturn(Optional.of(year2026));
        when(classRepository.existsByAcademicYearIdAndNameIgnoreCaseAndSection(1L, "Class 6", "A")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("Class 6", "a", null, 40)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Class 6 - A already exists in 2026");
        verify(classRepository, never()).save(any());
    }

    @Test
    void create_unknownYear_is404() {
        when(yearRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("Class 6", "A", null, 40)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Academic year 1 was not found");
    }

    @Test
    void update_duplicateOfAnotherClass_is409() {
        when(classRepository.findByIdWithYear(10L)).thenReturn(Optional.of(schoolClass(10, "Class 6", "A")));
        when(classRepository.existsByAcademicYearIdAndNameIgnoreCaseAndSectionAndIdNot(1L, "Class 6", "B", 10L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(10L, request("Class 6", "b", null, 40)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Class 6 - B already exists in this academic year");
    }

    @Test
    void update_capacityBelowEnrolled_is409() {
        when(classRepository.findByIdWithYear(10L)).thenReturn(Optional.of(schoolClass(10, "Class 6", "A")));
        when(classRepository.countStudents(10L)).thenReturn(30L);

        assertThatThrownBy(() -> service.update(10L, request("Class 6", "A", null, 29)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Capacity cannot be lower than the 30 students already enrolled");
        verify(classRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_capacityEqualToEnrolled_isSaved() {
        SchoolClass existing = schoolClass(10, "Class 6", "A");
        when(classRepository.findByIdWithYear(10L)).thenReturn(Optional.of(existing));
        when(classRepository.countStudents(10L)).thenReturn(30L);
        when(classRepository.saveAndFlush(existing)).thenReturn(existing);

        ClassResponse response = service.update(10L, request("Class 6", "A", " R-305 ", 30));

        assertThat(response.capacity()).isEqualTo(30);
        assertThat(response.room()).isEqualTo("R-305");
        assertThat(response.studentCount()).isEqualTo(30L);
    }

    @Test
    void update_unknownClass_is404() {
        when(classRepository.findByIdWithYear(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, request("Class 6", "A", null, 40)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Class 5 was not found");
    }

    @Test
    void delete_classWithStudents_is409() {
        when(classRepository.findByIdWithYear(10L)).thenReturn(Optional.of(schoolClass(10, "Class 6", "A")));
        when(classRepository.countStudents(10L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Move the students out of Class 6 - A before deleting it");
        verify(classRepository, never()).delete(any());
    }

    @Test
    void delete_emptyClass_isDeletedAndAudited() {
        SchoolClass existing = schoolClass(10, "Class 6", "A");
        when(classRepository.findByIdWithYear(10L)).thenReturn(Optional.of(existing));
        when(classRepository.countStudents(10L)).thenReturn(0L);

        service.delete(10L);

        verify(classRepository).delete(existing);
        verify(classRepository).flush();
        verify(auditService).record(eq("CLASS_DELETED"), eq("SchoolClass"), eq("10"), anyString());
    }
}
