package com.school.academic;

import com.school.academic.AcademicDtos.ClassSubjectRequest;
import com.school.academic.AcademicDtos.ClassSubjectResponse;
import com.school.audit.AuditService;
import com.school.common.exception.NotFoundException;
import com.school.security.AccessGuard;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassSubjectServiceTest {

    @Mock
    private ClassSubjectRepository classSubjectRepository;

    @Mock
    private SchoolClassRepository schoolClassRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private AccessGuard accessGuard;

    @Mock
    private AuditService auditService;

    private ClassSubjectService service;
    private SchoolClass schoolClass;
    private Subject physics;
    private Teacher teacher;

    @BeforeEach
    void setUp() {
        service = new ClassSubjectService(
                classSubjectRepository,
                schoolClassRepository,
                subjectRepository,
                teacherRepository,
                accessGuard,
                auditService
        );
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        schoolClass = new SchoolClass(year, "Class 9", "A", "R-101", 40);
        ReflectionTestUtils.setField(schoolClass, "id", 10L);
        physics = new Subject("PHY101", "Physics", 100, 33);
        ReflectionTestUtils.setField(physics, "id", 20L);
        teacher = new Teacher(
                null, "T-1001", "Ayesha", "Rahman", "ayesha@school.edu",
                "01700000000", "Lecturer", LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(teacher, "id", 7L);
    }

    @Test
    void listForClass_checksManageAccess_andReturnsOrderedDtos() {
        ClassSubject assignment = new ClassSubject(schoolClass, physics, teacher);
        ReflectionTestUtils.setField(assignment, "id", 1L);
        when(classSubjectRepository.findAllByClassIdWithDetails(10L)).thenReturn(List.of(assignment));

        List<ClassSubjectResponse> result = service.listForClass(10L);

        verify(accessGuard).checkCanManageClass(10L);
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.subjectCode()).isEqualTo("PHY101");
            assertThat(item.teacherId()).isEqualTo(7L);
            assertThat(item.teacherName()).isEqualTo("Ayesha Rahman");
        });
    }

    @Test
    void listForClass_whenGuardDenies_propagates403WithoutQuerying() {
        doThrow(new AccessDeniedException("You do not teach in this class"))
                .when(accessGuard).checkCanManageClass(10L);

        assertThatThrownBy(() -> service.listForClass(10L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not teach in this class");
        verifyNoInteractions(classSubjectRepository);
    }

    @Test
    void assign_createsOrUpdates_allowsNullTeacher_andAudits() {
        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(schoolClass));
        when(subjectRepository.findById(20L)).thenReturn(Optional.of(physics));
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));
        when(classSubjectRepository.findBySchoolClassIdAndSubjectIdWithDetails(10L, 20L)).thenReturn(Optional.empty());
        when(classSubjectRepository.saveAndFlush(any(ClassSubject.class))).thenAnswer(inv -> {
            ClassSubject cs = inv.getArgument(0);
            ReflectionTestUtils.setField(cs, "id", 50L);
            return cs;
        });

        ClassSubjectResponse created = service.assign(10L, new ClassSubjectRequest(20L, 7L));
        assertThat(created.id()).isEqualTo(50L);
        assertThat(created.teacherId()).isEqualTo(7L);
        assertThat(created.teacherName()).isEqualTo("Ayesha Rahman");
        verify(auditService).record(eq("SUBJECT_ASSIGNED"), eq("ClassSubject"), eq("50"), contains("PHY101"));

        ClassSubject existing = new ClassSubject(schoolClass, physics, teacher);
        ReflectionTestUtils.setField(existing, "id", 50L);
        when(classSubjectRepository.findBySchoolClassIdAndSubjectIdWithDetails(10L, 20L))
                .thenReturn(Optional.of(existing));
        when(classSubjectRepository.saveAndFlush(existing)).thenReturn(existing);

        ClassSubjectResponse unassigned = service.assign(10L, new ClassSubjectRequest(20L, null));
        assertThat(unassigned.teacherId()).isNull();
        assertThat(unassigned.teacherName()).isNull();
    }

    @Test
    void assign_missingClassSubjectOrTeacher_throws404WithExactMessage() {
        when(schoolClassRepository.findByIdWithYear(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(99L, new ClassSubjectRequest(20L, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Class 99 was not found");

        when(schoolClassRepository.findByIdWithYear(10L)).thenReturn(Optional.of(schoolClass));
        when(subjectRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(10L, new ClassSubjectRequest(99L, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subject 99 was not found");

        when(subjectRepository.findById(20L)).thenReturn(Optional.of(physics));
        when(teacherRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(10L, new ClassSubjectRequest(20L, 99L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Teacher 99 was not found");
    }

    @Test
    void remove_deletesAndFlushes_andMissingAssignmentThrows404() {
        when(schoolClassRepository.existsById(10L)).thenReturn(true);
        ClassSubject existing = new ClassSubject(schoolClass, physics, teacher);
        when(classSubjectRepository.findBySchoolClassIdAndSubjectIdWithDetails(10L, 20L))
                .thenReturn(Optional.of(existing));

        service.remove(10L, 20L);
        verify(classSubjectRepository).delete(existing);
        verify(classSubjectRepository).flush();

        when(classSubjectRepository.findBySchoolClassIdAndSubjectIdWithDetails(10L, 99L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(10L, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("This subject is not assigned to the class");
    }
}
