package com.school.teacher;

import com.school.academic.AcademicYear;
import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.audit.AuditService;
import com.school.common.PageResponse;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.security.AccessGuard;
import com.school.teacher.TeacherDtos.CreatedTeacherResponse;
import com.school.teacher.TeacherDtos.StatusRequest;
import com.school.teacher.TeacherDtos.TeacherRequest;
import com.school.teacher.TeacherDtos.TeacherResponse;
import com.school.teacher.TeacherDtos.TeachingLoad;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import com.school.user.UserService;
import com.school.user.UserService.NewAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Asia/Dhaka"));

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private SchoolClassRepository schoolClassRepository;

    @Mock
    private ClassSubjectRepository classSubjectRepository;

    @Mock
    private AccessGuard accessGuard;

    @Mock
    private UserService userService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuditService auditService;

    private TeacherService teacherService;

    @BeforeEach
    void setUp() {
        teacherService = new TeacherService(
                teacherRepository,
                schoolClassRepository,
                classSubjectRepository,
                accessGuard,
                userService,
                appUserRepository,
                auditService,
                FIXED_CLOCK
        );
    }

    @Test
    void create_normalizesFields_createsTeacherAndAccountInSameTx_andAudits() {
        AppUser user = new AppUser("t-1007", "ayesha@school.edu", "$2a$12$hash", "Ayesha Rahman", Role.TEACHER);
        ReflectionTestUtils.setField(user, "id", 11L);
        when(teacherRepository.existsByEmployeeNoIgnoreCase("T-1007")).thenReturn(false);
        when(teacherRepository.existsByEmailIgnoreCase("ayesha@school.edu")).thenReturn(false);
        when(userService.createAccount("t-1007", "ayesha@school.edu", "Ayesha Rahman", Role.TEACHER))
                .thenReturn(new NewAccount(user, "TempPass1234"));
        when(teacherRepository.saveAndFlush(any(Teacher.class))).thenAnswer(invocation -> {
            Teacher saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        CreatedTeacherResponse response = teacherService.create(new TeacherRequest(
                "  t-1007 ",
                " Ayesha ",
                " Rahman ",
                " Ayesha@School.EDU ",
                " +8801711111111 ",
                " Senior Lecturer ",
                LocalDate.of(2026, 11, 25)
        ));

        assertThat(response.teacher().id()).isEqualTo(5L);
        assertThat(response.teacher().employeeNo()).isEqualTo("T-1007");
        assertThat(response.teacher().email()).isEqualTo("ayesha@school.edu");
        assertThat(response.teacher().fullName()).isEqualTo("Ayesha Rahman");
        assertThat(response.teacher().status()).isEqualTo(TeacherStatus.ACTIVE);
        assertThat(response.teacher().username()).isEqualTo("t-1007");
        assertThat(response.account().username()).isEqualTo("t-1007");
        assertThat(response.account().temporaryPassword()).isEqualTo("TempPass1234");

        verify(auditService).record(
                eq("TEACHER_CREATED"),
                eq("Teacher"),
                eq("5"),
                contains("T-1007")
        );
    }

    @Test
    void create_duplicateEmployeeNo_throws409WithExactMessage() {
        when(teacherRepository.existsByEmployeeNoIgnoreCase("T-1007")).thenReturn(true);

        assertThatThrownBy(() -> teacherService.create(new TeacherRequest(
                " t-1007 ",
                "Ayesha",
                "Rahman",
                "ayesha@school.edu",
                "+8801711111111",
                "Lecturer",
                LocalDate.of(2026, 1, 10)
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Employee number T-1007 already exists");

        verifyNoInteractions(userService, auditService);
        verify(teacherRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_duplicateEmail_throws409WithExactMessage() {
        when(teacherRepository.existsByEmployeeNoIgnoreCase("T-1008")).thenReturn(false);
        when(teacherRepository.existsByEmailIgnoreCase("ayesha@school.edu")).thenReturn(true);

        assertThatThrownBy(() -> teacherService.create(new TeacherRequest(
                "T-1008",
                "Ayesha",
                "Rahman",
                "Ayesha@School.edu",
                "+8801711111111",
                "Lecturer",
                LocalDate.of(2026, 1, 10)
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email ayesha@school.edu is already used by another teacher");

        verifyNoInteractions(userService, auditService);
        verify(teacherRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_futureJoiningDateInDhakaClock_throws422() {
        assertThatThrownBy(() -> teacherService.create(new TeacherRequest(
                "T-1009",
                "Ayesha",
                "Rahman",
                "ayesha@school.edu",
                "+8801711111111",
                "Lecturer",
                LocalDate.of(2026, 11, 26)
        )))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Joining date cannot be in the future");

        verifyNoInteractions(teacherRepository, userService, auditService);
    }

    @Test
    void update_keepsEmployeeNoImmutable_andSyncsEmailAndNameToLinkedUser() {
        AppUser user = new AppUser("t-1007", "old@school.edu", "$2a$12$hash", "Old Name", Role.TEACHER);
        Teacher existing = new Teacher(
                user,
                "T-1007",
                "Old",
                "Name",
                "old@school.edu",
                "01700000000",
                "Lecturer",
                LocalDate.of(2024, 1, 1)
        );
        ReflectionTestUtils.setField(existing, "id", 7L);

        when(teacherRepository.findByIdWithUser(7L)).thenReturn(Optional.of(existing));
        when(teacherRepository.existsByEmailIgnoreCaseAndIdNot("new@school.edu", 7L)).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCase("new@school.edu")).thenReturn(false);
        when(teacherRepository.saveAndFlush(existing)).thenReturn(existing);

        TeacherResponse updated = teacherService.update(7L, new TeacherRequest(
                "T-9999",
                " Karim ",
                " Uddin ",
                " NEW@School.EDU ",
                " +8801711112222 ",
                " Professor ",
                LocalDate.of(2025, 2, 1)
        ));

        assertThat(updated.employeeNo()).isEqualTo("T-1007");
        assertThat(updated.firstName()).isEqualTo("Karim");
        assertThat(updated.lastName()).isEqualTo("Uddin");
        assertThat(updated.email()).isEqualTo("new@school.edu");
        assertThat(user.getEmail()).isEqualTo("new@school.edu");
        assertThat(user.getFullName()).isEqualTo("Karim Uddin");
    }

    @Test
    void update_duplicateEmail_throws409WithExactMessage() {
        Teacher existing = new Teacher(
                null,
                "T-1007",
                "Karim",
                "Uddin",
                "karim@school.edu",
                "01700000000",
                "Lecturer",
                LocalDate.of(2024, 1, 1)
        );
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(teacherRepository.findByIdWithUser(7L)).thenReturn(Optional.of(existing));
        when(teacherRepository.existsByEmailIgnoreCaseAndIdNot("taken@school.edu", 7L)).thenReturn(true);

        assertThatThrownBy(() -> teacherService.update(7L, new TeacherRequest(
                "T-1007",
                "Karim",
                "Uddin",
                "TAKEN@school.edu",
                "01700000000",
                "Lecturer",
                LocalDate.of(2024, 1, 1)
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email taken@school.edu is already used by another teacher");
    }

    @Test
    void get_missingTeacher_throws404WithExactMessage() {
        when(teacherRepository.findByIdWithUser(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Teacher 99 was not found");
    }

    @Test
    void updateStatus_resigned_disablesLinkedAccountAndAudits() {
        AppUser user = new AppUser("t-1007", "karim@school.edu", "$2a$12$hash", "Karim Uddin", Role.TEACHER);
        Teacher existing = new Teacher(
                user,
                "T-1007",
                "Karim",
                "Uddin",
                "karim@school.edu",
                "01700000000",
                "Lecturer",
                LocalDate.of(2024, 1, 1)
        );
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(teacherRepository.findByIdWithUser(7L)).thenReturn(Optional.of(existing));
        when(teacherRepository.saveAndFlush(existing)).thenReturn(existing);

        TeacherResponse response = teacherService.updateStatus(7L, new StatusRequest(TeacherStatus.RESIGNED));

        assertThat(response.status()).isEqualTo(TeacherStatus.RESIGNED);
        verify(userService).disable(user);
        verify(auditService).record(
                eq("TEACHER_STATUS_CHANGED"),
                eq("Teacher"),
                eq("7"),
                contains("RESIGNED")
        );
    }

    @Test
    void updateStatus_onLeave_keepsAccountEnabledAndAudits() {
        AppUser user = new AppUser("t-1007", "karim@school.edu", "$2a$12$hash", "Karim Uddin", Role.TEACHER);
        Teacher existing = new Teacher(
                user,
                "T-1007",
                "Karim",
                "Uddin",
                "karim@school.edu",
                "01700000000",
                "Lecturer",
                LocalDate.of(2024, 1, 1)
        );
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(teacherRepository.findByIdWithUser(7L)).thenReturn(Optional.of(existing));
        when(teacherRepository.saveAndFlush(existing)).thenReturn(existing);

        TeacherResponse response = teacherService.updateStatus(7L, new StatusRequest(TeacherStatus.ON_LEAVE));

        assertThat(response.status()).isEqualTo(TeacherStatus.ON_LEAVE);
        verify(userService, never()).disable(any());
        verify(auditService).record(
                eq("TEACHER_STATUS_CHANGED"),
                eq("Teacher"),
                eq("7"),
                contains("ON_LEAVE")
        );
    }

    @Test
    void list_escapesWildcardsAndUsesDefaultSortWhenUnsorted() {
        when(teacherRepository.searchByStatusWithUser(
                eq(TeacherStatus.ACTIVE),
                eq("%100!%!_t!%%"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        PageResponse<TeacherResponse> response = teacherService.list(
                " 100%_T% ",
                TeacherStatus.ACTIVE,
                PageRequest.of(0, 20)
        );

        assertThat(response.content()).isEmpty();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(teacherRepository).searchByStatusWithUser(
                eq(TeacherStatus.ACTIVE),
                eq("%100!%!_t!%%"),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getSort())
                .isEqualTo(Sort.by(
                        Sort.Order.asc("firstName"),
                        Sort.Order.asc("lastName"),
                        Sort.Order.asc("id")
                ));
    }

    @Test
    void list_withoutSearchOrStatus_callsFindAllWithUser() {
        when(teacherRepository.findAllWithUser(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        teacherService.list("   ", null, PageRequest.of(0, 20, Sort.by("firstName")));

        verify(teacherRepository).findAllWithUser(PageRequest.of(0, 20, Sort.by("firstName")));
        verify(teacherRepository, never()).searchWithUser(anyString(), any());
    }

    @Test
    void list_unknownSortField_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> teacherService.list(null, null, PageRequest.of(0, 20, Sort.by("badField"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid sort field: badField");

        verifyNoInteractions(teacherRepository);
    }

    @Test
    void teachingLoad_returnsClassTeacherAndSubjects_andMissingTeacherThrows404() {
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        SchoolClass schoolClass = new SchoolClass(year, "Class 9", "A", "R-101", 40);
        ReflectionTestUtils.setField(schoolClass, "id", 10L);
        Subject subject = new Subject("PHY101", "Physics", 100, 33);
        ReflectionTestUtils.setField(subject, "id", 20L);
        ClassSubject assignment = new ClassSubject(schoolClass, subject, null);

        when(teacherRepository.existsById(7L)).thenReturn(true);
        when(schoolClassRepository.findAllByClassTeacherIdWithYear(7L)).thenReturn(List.of(schoolClass));
        when(classSubjectRepository.findAllByTeacherIdWithDetails(7L)).thenReturn(List.of(assignment));

        TeachingLoad load = teacherService.teachingLoad(7L);
        assertThat(load.classTeacherOf()).singleElement().satisfies(ref -> {
            assertThat(ref.classId()).isEqualTo(10L);
            assertThat(ref.className()).isEqualTo("Class 9 - A");
            assertThat(ref.academicYear()).isEqualTo("2026");
        });
        assertThat(load.subjects()).singleElement().satisfies(sub -> {
            assertThat(sub.classId()).isEqualTo(10L);
            assertThat(sub.subjectId()).isEqualTo(20L);
            assertThat(sub.subjectCode()).isEqualTo("PHY101");
            assertThat(sub.subjectName()).isEqualTo("Physics");
        });

        when(teacherRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> teacherService.teachingLoad(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Teacher 99 was not found");
    }

    @Test
    void myTeachingLoad_usesCurrentTeacherFromAccessGuard() {
        Teacher teacher = new Teacher(
                null, "T-1007", "Ayesha", "Rahman", "ayesha@school.edu",
                "01700000000", "Lecturer", LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(teacher, "id", 7L);
        when(accessGuard.currentTeacher()).thenReturn(teacher);
        when(schoolClassRepository.findAllByClassTeacherIdWithYear(7L)).thenReturn(List.of());
        when(classSubjectRepository.findAllByTeacherIdWithDetails(7L)).thenReturn(List.of());

        TeachingLoad load = teacherService.myTeachingLoad();
        assertThat(load.classTeacherOf()).isEmpty();
        assertThat(load.subjects()).isEmpty();
        verify(accessGuard).currentTeacher();
    }
}
