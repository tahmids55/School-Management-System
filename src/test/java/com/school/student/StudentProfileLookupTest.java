package com.school.student;

import com.school.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentProfileLookupTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentProfileLookup lookup;

    @Test
    void student_getsTheLinkedStudentId() {
        when(studentRepository.findIdByUsername("adm-1")).thenReturn(Optional.of(7L));

        assertThat(lookup.findProfileId(Role.STUDENT, "adm-1")).contains(7L);
    }

    @Test
    void otherRolesAndBlankNames_areNotLookedUp() {
        assertThat(lookup.findProfileId(Role.TEACHER, "adm-1")).isEmpty();
        assertThat(lookup.findProfileId(Role.ADMIN, "admin")).isEmpty();
        assertThat(lookup.findProfileId(Role.STUDENT, " ")).isEmpty();
        verifyNoInteractions(studentRepository);
    }
}
