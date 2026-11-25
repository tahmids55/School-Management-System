package com.school.teacher;

import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherProfileLookupTest {

    @Mock
    private TeacherRepository teacherRepository;

    private TeacherProfileLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new TeacherProfileLookup(teacherRepository);
    }

    @Test
    void findProfileId_forTeacherRole_returnsTeacherId() {
        when(teacherRepository.findIdByUserUsernameIgnoreCase("t-1007")).thenReturn(Optional.of(42L));

        Optional<Long> result = lookup.findProfileId(Role.TEACHER, " t-1007 ");

        assertThat(result).contains(42L);
        verify(teacherRepository).findIdByUserUsernameIgnoreCase("t-1007");
    }

    @Test
    void findProfileId_forNonTeacherRole_returnsEmptyWithoutQuery() {
        assertThat(lookup.findProfileId(Role.ADMIN, "admin")).isEmpty();
        assertThat(lookup.findProfileId(Role.STUDENT, "adm-1")).isEmpty();
        verifyNoInteractions(teacherRepository);
    }

    @Test
    void findProfileId_forBlankUsername_returnsEmptyWithoutQuery() {
        assertThat(lookup.findProfileId(Role.TEACHER, null)).isEmpty();
        assertThat(lookup.findProfileId(Role.TEACHER, "   ")).isEmpty();
        verifyNoInteractions(teacherRepository);
    }
}
