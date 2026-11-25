package com.school.security;

import com.school.common.exception.ForbiddenException;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAccessGuardTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeachingScope teachingScope;

    @InjectMocks
    private StudentAccessGuard guard;

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void studentLinkedTo(String username, long studentId) {
        Student student = mockStudent(studentId);
        when(studentRepository.findByUsername(username)).thenReturn(Optional.of(student));
    }

    private static Student mockStudent(long id) {
        Student student = new Student("ADM-" + id);
        ReflectionTestUtils.setField(student, "id", id);
        return student;
    }

    @Test
    void admin_mayViewEveryStudentAndClass_withoutAnyLookup() {
        loginAs("admin", "ADMIN");

        assertThatCode(() -> guard.checkCanViewStudent(99L)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkCanViewClass(5L)).doesNotThrowAnyException();
        verifyNoInteractions(studentRepository, teachingScope);
    }

    @Test
    void student_mayViewOnlyThemselves() {
        loginAs("adm-1", "STUDENT");
        studentLinkedTo("adm-1", 1L);

        assertThatCode(() -> guard.checkCanViewStudent(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkCanViewStudent(2L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only view your own records");
    }

    @Test
    void studentLoginWithoutProfile_isForbidden() {
        loginAs("ghost", "STUDENT");
        when(studentRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.currentStudent())
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("No student profile is linked to this account");
        assertThatThrownBy(() -> guard.checkCanViewStudent(1L))
                .hasMessage("No student profile is linked to this account");
    }

    @Test
    void currentStudent_returnsTheLinkedRecord() {
        loginAs("adm-7", "STUDENT");
        studentLinkedTo("adm-7", 7L);

        assertThat(guard.currentStudent().getId()).isEqualTo(7L);
    }

    @Test
    void teacher_mayViewStudentsOfOwnClassesOnly() {
        loginAs("t-1", "TEACHER");
        when(teachingScope.accessibleClassIds("t-1")).thenReturn(Set.of(10L, 11L));
        when(studentRepository.findClassIdById(1L)).thenReturn(Optional.of(10L));
        when(studentRepository.findClassIdById(2L)).thenReturn(Optional.of(12L));

        assertThatCode(() -> guard.checkCanViewStudent(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkCanViewStudent(2L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only view students of your own classes");
        assertThatCode(() -> guard.checkCanViewClass(11L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkCanViewClass(12L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void teacher_withoutClasses_isDeniedEverything() {
        loginAs("t-2", "TEACHER");
        when(teachingScope.accessibleClassIds("t-2")).thenReturn(Set.of());
        when(studentRepository.findClassIdById(1L)).thenReturn(Optional.of(10L));

        assertThat(guard.teacherClassIds()).isEmpty();
        assertThatThrownBy(() -> guard.checkCanViewStudent(1L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.checkCanViewClass(10L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void teacher_isDeniedStudentsWithoutClass_andUnknownStudents_andNullScope() {
        loginAs("t-3", "TEACHER");
        when(studentRepository.findClassIdById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.checkCanViewStudent(1L)).isInstanceOf(ForbiddenException.class);
        when(teachingScope.accessibleClassIds("t-3")).thenReturn(null);
        assertThat(guard.teacherClassIds()).isEmpty();
    }

    @Test
    void studentAndAnonymous_mayNotViewARoster() {
        loginAs("adm-1", "STUDENT");
        assertThatThrownBy(() -> guard.checkCanViewClass(10L)).isInstanceOf(ForbiddenException.class);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> guard.checkCanViewStudent(1L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.checkCanViewClass(10L)).isInstanceOf(ForbiddenException.class);
    }
}
