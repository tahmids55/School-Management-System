package com.school.security;

import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClassRepository;
import com.school.common.exception.NotFoundException;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessGuardTest {

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private SchoolClassRepository schoolClassRepository;

    @Mock
    private ClassSubjectRepository classSubjectRepository;

    private AccessGuard accessGuard;
    private TeacherTeachingScope teachingScope;

    @BeforeEach
    void setUp() {
        accessGuard = new AccessGuard(teacherRepository, schoolClassRepository, classSubjectRepository);
        teachingScope = new TeacherTeachingScope(teacherRepository, schoolClassRepository, classSubjectRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static Teacher teacher(long id) {
        Teacher teacher = new Teacher(
                null, "T-1001", "Ayesha", "Rahman", "ayesha@school.edu",
                "01700000000", "Lecturer", LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(teacher, "id", id);
        return teacher;
    }

    @Test
    void currentTeacher_missingLoginOrProfile_throws403WithExactMessage() {
        assertThatThrownBy(() -> accessGuard.currentTeacher())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No teacher profile is linked to this account");

        loginAs("t-9999", "TEACHER");
        when(teacherRepository.findByUserUsernameIgnoreCase("t-9999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accessGuard.currentTeacher())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No teacher profile is linked to this account");
    }

    @Test
    void checkCanManageClass_adminAndClassTeacherAndSubjectTeacherAllowed_unrelatedDenied_missing404() {
        when(schoolClassRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> accessGuard.checkCanManageClass(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Class 99 was not found");

        when(schoolClassRepository.existsById(10L)).thenReturn(true);
        loginAs("admin", "ADMIN");
        assertThat(accessGuard.isAdmin()).isTrue();
        assertThatCode(() -> accessGuard.checkCanManageClass(10L)).doesNotThrowAnyException();
        verifyNoInteractions(teacherRepository);

        loginAs("t-1001", "TEACHER");
        Teacher t = teacher(7L);
        when(teacherRepository.findByUserUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(t));
        when(schoolClassRepository.existsByIdAndClassTeacherId(10L, 7L)).thenReturn(true);
        assertThatCode(() -> accessGuard.checkCanManageClass(10L)).doesNotThrowAnyException();

        when(schoolClassRepository.existsByIdAndClassTeacherId(10L, 7L)).thenReturn(false);
        when(classSubjectRepository.existsBySchoolClassIdAndTeacherId(10L, 7L)).thenReturn(true);
        assertThatCode(() -> accessGuard.checkCanManageClass(10L)).doesNotThrowAnyException();

        when(classSubjectRepository.existsBySchoolClassIdAndTeacherId(10L, 7L)).thenReturn(false);
        assertThatThrownBy(() -> accessGuard.checkCanManageClass(10L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not teach in this class");
    }

    @Test
    void checkCanGradeSubject_exactAssignmentOnly_classTeacherDeniedWithoutSubjectAssignment() {
        when(schoolClassRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> accessGuard.checkCanGradeSubject(99L, 5L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Class 99 was not found");

        when(schoolClassRepository.existsById(10L)).thenReturn(true);
        loginAs("admin", "ADMIN");
        assertThatCode(() -> accessGuard.checkCanGradeSubject(10L, 5L)).doesNotThrowAnyException();

        loginAs("t-1001", "TEACHER");
        Teacher t = teacher(7L);
        when(teacherRepository.findByUserUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(t));
        when(classSubjectRepository.existsBySchoolClassIdAndSubjectIdAndTeacherId(10L, 5L, 7L)).thenReturn(true);
        assertThatCode(() -> accessGuard.checkCanGradeSubject(10L, 5L)).doesNotThrowAnyException();

        when(classSubjectRepository.existsBySchoolClassIdAndSubjectIdAndTeacherId(10L, 6L, 7L)).thenReturn(false);
        assertThatThrownBy(() -> accessGuard.checkCanGradeSubject(10L, 6L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("This subject is not assigned to you in this class");
    }

    @Test
    void teachingScope_unionsClassTeacherAndSubjectAssignments() {
        assertThat(teachingScope.accessibleClassIds(null)).isEmpty();
        assertThat(teachingScope.accessibleClassIds("  ")).isEmpty();

        when(teacherRepository.findIdByUserUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(7L));
        when(schoolClassRepository.findIdsByClassTeacherId(7L)).thenReturn(List.of(10L, 11L));
        when(classSubjectRepository.findClassIdsByTeacherId(7L)).thenReturn(List.of(11L, 12L));

        assertThat(teachingScope.accessibleClassIds(" t-1001 ")).containsExactlyInAnyOrder(10L, 11L, 12L);
    }
}
