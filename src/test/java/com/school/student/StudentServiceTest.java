package com.school.student;

import com.school.academic.AcademicYear;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.audit.AuditService;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.student.StudentDtos.CreatedStudentResponse;
import com.school.student.StudentDtos.StatusRequest;
import com.school.student.StudentDtos.StudentRequest;
import com.school.student.StudentDtos.StudentResponse;
import com.school.user.AppUser;
import com.school.user.Role;
import com.school.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private SchoolClassRepository classRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private StudentService service;

    private SchoolClass class6A;

    @BeforeEach
    void setUp() {
        service = new StudentService(studentRepository, classRepository, userService, auditService,
                new StudentCsvExporter());
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        class6A = schoolClass(10L, "Class 6", "A", 40);
        class6A.setAcademicYear(year);
    }

    private static SchoolClass schoolClass(long id, String name, String section, int capacity) {
        SchoolClass schoolClass = new SchoolClass(null, name, section, null, capacity);
        ReflectionTestUtils.setField(schoolClass, "id", id);
        return schoolClass;
    }

    private static StudentRequest request(String admissionNo, String email, Long classId, Integer rollNo) {
        return new StudentRequest(admissionNo, " Rahim ", " Uddin ", Gender.MALE, LocalDate.of(2014, 3, 9),
                email, "  ", " ", "Abdul Uddin", "01711111111", "Father", classId, rollNo,
                LocalDate.of(2026, 1, 5), false);
    }

    /** An existing student with id 1, ACTIVE, optionally in a class. */
    private Student existing(SchoolClass schoolClass, Integer rollNo) {
        Student student = new Student("ADM-1");
        ReflectionTestUtils.setField(student, "id", 1L);
        student.setFirstName("Rahim");
        student.setLastName("Uddin");
        student.setSchoolClass(schoolClass);
        student.setRollNo(rollNo);
        when(studentRepository.findByIdWithClassAndUser(1L)).thenReturn(Optional.of(student));
        return student;
    }

    private void savesAndGivesId() {
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            if (student.getId() == null) {
                ReflectionTestUtils.setField(student, "id", 100L);
            }
            return student;
        });
    }

    private void classHasActive(SchoolClass schoolClass, long active) {
        when(classRepository.findByIdForUpdate(schoolClass.getId())).thenReturn(Optional.of(schoolClass));
        when(studentRepository.countBySchoolClassIdAndStatus(schoolClass.getId(), StudentStatus.ACTIVE))
                .thenReturn(active);
    }

    // ---------- create ----------

    @Test
    void create_normalisesValues_assignsNextRoll_andAudits() {
        classHasActive(class6A, 4);
        when(studentRepository.findMaxRollNo(10L)).thenReturn(4);
        savesAndGivesId();

        CreatedStudentResponse created = service.create(request(" adm-2026-101 ", " Rahim@School.EDU ", 10L, null));

        StudentResponse student = created.student();
        assertThat(student.id()).isEqualTo(100L);
        assertThat(student.admissionNo()).isEqualTo("ADM-2026-101");
        assertThat(student.email()).isEqualTo("rahim@school.edu");
        assertThat(student.firstName()).isEqualTo("Rahim");
        assertThat(student.fullName()).isEqualTo("Rahim Uddin");
        assertThat(student.phone()).isNull();
        assertThat(student.address()).isNull();
        assertThat(student.status()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(student.className()).isEqualTo("Class 6 - A");
        assertThat(student.rollNo()).isEqualTo(5);
        assertThat(created.account()).isNull();
        verify(studentRepository).existsByEmail("rahim@school.edu");
        verify(auditService).record(eq(StudentService.AUDIT_ADMITTED), eq("Student"), eq("100"),
                eq("Admitted Rahim Uddin (ADM-2026-101) to Class 6 - A"));
    }

    @Test
    void create_firstStudentInAClass_getsRoll1() {
        classHasActive(class6A, 0);
        when(studentRepository.findMaxRollNo(10L)).thenReturn(null);
        savesAndGivesId();

        assertThat(service.create(request("ADM-1", null, 10L, null)).student().rollNo()).isEqualTo(1);
    }

    @Test
    void create_duplicateAdmissionNo_is409() {
        when(studentRepository.existsByAdmissionNo("ADM-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("adm-1", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Admission number ADM-1 already exists");
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_duplicateEmail_is409_blankEmailIsNotChecked() {
        when(studentRepository.existsByEmail("taken@school.edu")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ADM-1", "Taken@School.edu", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email taken@school.edu is already used by another student");

        savesAndGivesId();
        assertThat(service.create(request("ADM-2", "   ", null, null)).student().email()).isNull();
        verify(studentRepository, never()).existsByEmail("");
    }

    @Test
    void create_withoutClass_hasNoRoll_evenWhenOneIsSent() {
        savesAndGivesId();

        StudentResponse student = service.create(request("ADM-1", null, null, 7)).student();

        assertThat(student.classId()).isNull();
        assertThat(student.rollNo()).isNull();
        verifyNoInteractions(classRepository);
        verify(auditService).record(anyString(), anyString(), anyString(), contains("without a class"));
    }

    @Test
    void create_unknownClass_is404() {
        when(classRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("ADM-1", null, 99L, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Class 99 was not found");
    }

    @Test
    void capacity_lastFreeSeatIsAllowed_fullClassIs409() {
        classHasActive(class6A, 39);
        when(studentRepository.findMaxRollNo(10L)).thenReturn(39);
        savesAndGivesId();
        assertThat(service.create(request("ADM-40", null, 10L, null)).student().rollNo()).isEqualTo(40);

        when(studentRepository.countBySchoolClassIdAndStatus(10L, StudentStatus.ACTIVE)).thenReturn(40L);
        assertThatThrownBy(() -> service.create(request("ADM-41", null, 10L, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Class 6 - A is full (capacity 40)");
    }

    @Test
    void create_takenRoll_is409() {
        classHasActive(class6A, 3);
        when(studentRepository.existsBySchoolClassIdAndRollNo(10L, 2)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ADM-1", null, 10L, 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Roll number 2 is already taken in Class 6 - A");
        verify(studentRepository, never()).findMaxRollNo(anyLong());
    }

    // ---------- update ----------

    @Test
    void update_keepsAdmissionNoAndRoll_whenStayingInTheClass_withoutCapacityCheck() {
        existing(class6A, 8);
        savesAndGivesId();

        StudentResponse student = service.update(1L, request("CHANGED-1", null, 10L, null));

        assertThat(student.admissionNo()).isEqualTo("ADM-1");
        assertThat(student.rollNo()).isEqualTo(8);
        verify(classRepository, never()).findByIdForUpdate(anyLong());
        verify(studentRepository, never()).countBySchoolClassIdAndStatus(anyLong(), any());
        verify(studentRepository).existsBySchoolClassIdAndRollNoAndIdNot(10L, 8, 1L);
    }

    @Test
    void update_movingToAnotherClass_checksCapacity_andTakesTheNextRoll() {
        existing(class6A, 8);
        SchoolClass class6B = schoolClass(11L, "Class 6", "B", 30);
        classHasActive(class6B, 12);
        when(studentRepository.findMaxRollNo(11L)).thenReturn(12);
        savesAndGivesId();

        StudentResponse student = service.update(1L, request("ADM-1", null, 11L, null));

        assertThat(student.classId()).isEqualTo(11L);
        assertThat(student.className()).isEqualTo("Class 6 - B");
        assertThat(student.rollNo()).isEqualTo(13);
    }

    @Test
    void update_movingIntoAFullClass_is409_butAnInactiveStudentDoesNotNeedASeat() {
        Student student = existing(null, null);
        SchoolClass full = schoolClass(11L, "Class 6", "B", 2);
        classHasActive(full, 2);

        assertThatThrownBy(() -> service.update(1L, request("ADM-1", null, 11L, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Class 6 - B is full (capacity 2)");

        student.setStatus(StudentStatus.GRADUATED);
        when(studentRepository.findMaxRollNo(11L)).thenReturn(2);
        savesAndGivesId();
        assertThat(service.update(1L, request("ADM-1", null, 11L, null)).rollNo()).isEqualTo(3);
    }

    @Test
    void update_removingTheClass_clearsTheRoll() {
        existing(class6A, 8);
        savesAndGivesId();

        StudentResponse student = service.update(1L, request("ADM-1", null, null, 8));

        assertThat(student.classId()).isNull();
        assertThat(student.rollNo()).isNull();
    }

    @Test
    void update_emailOfAnotherStudent_is409_unknownStudentIs404() {
        existing(null, null);
        when(studentRepository.existsByEmailAndIdNot("taken@school.edu", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("ADM-1", "taken@school.edu", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email taken@school.edu is already used by another student");
        assertThatThrownBy(() -> service.update(5L, request("ADM-1", null, null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Student 5 was not found");
    }

    // ---------- status (soft delete) ----------

    @Test
    void changeStatus_toNonActive_disablesTheLogin_keepsTheRecord_andAudits() {
        Student student = existing(class6A, 8);
        AppUser login = new AppUser("adm-1", "adm-1@school.edu", "hash", "Rahim Uddin", Role.STUDENT);
        student.setUser(login);
        savesAndGivesId();

        StudentResponse response = service.changeStatus(1L, new StatusRequest(StudentStatus.GRADUATED));

        assertThat(response.status()).isEqualTo(StudentStatus.GRADUATED);
        assertThat(response.classId()).isEqualTo(10L);
        assertThat(response.rollNo()).isEqualTo(8);
        assertThat(response.username()).isEqualTo("adm-1");
        verify(userService).disable(login);
        verify(studentRepository, never()).delete(any(Student.class));
        verify(auditService).record(StudentService.AUDIT_STATUS_CHANGED, "Student", "1",
                "Rahim Uddin (ADM-1): ACTIVE -> GRADUATED");
    }

    @Test
    void changeStatus_toTheSameStatus_changesNothing() {
        existing(class6A, 8);

        service.changeStatus(1L, new StatusRequest(StudentStatus.ACTIVE));

        verify(studentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userService, auditService);
    }

    @Test
    void changeStatus_reactivatingInAFullClass_is409() {
        Student student = existing(class6A, 8);
        student.setStatus(StudentStatus.INACTIVE);
        classHasActive(class6A, 40);

        assertThatThrownBy(() -> service.changeStatus(1L, new StatusRequest(StudentStatus.ACTIVE)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Class 6 - A is full (capacity 40)");
        verifyNoInteractions(userService);
    }

    @Test
    void reactivating_doesNotDisableTheLogin() {
        Student student = existing(null, null);
        student.setStatus(StudentStatus.INACTIVE);
        savesAndGivesId();

        assertThat(service.changeStatus(1L, new StatusRequest(StudentStatus.ACTIVE)).status())
                .isEqualTo(StudentStatus.ACTIVE);
        verifyNoInteractions(userService);
    }

    // ---------- reads ----------

    @Test
    void roster_ofAnUnknownClass_is404() {
        when(classRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.roster(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Class 99 was not found");
    }

    @Test
    void searchFilters_skipBlankValues() {
        assertThat(StudentSpecifications.matches("  ")).isNull();
        assertThat(StudentSpecifications.inClass(null)).isNull();
        assertThat(StudentSpecifications.hasStatus(null)).isNull();
        assertThat(StudentSpecifications.escapeLike("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }

    @Test
    void create_withAChosenFreeRoll_savesTheLockedClassAndThatRoll() {
        classHasActive(class6A, 0);
        savesAndGivesId();

        service.create(request("ADM-1", null, 10L, 4));

        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSchoolClass()).isSameAs(class6A);
        assertThat(saved.getValue().getRollNo()).isEqualTo(4);
        verify(studentRepository, never()).findMaxRollNo(anyLong());
    }
}
