package com.school.attendance;

import com.school.IntegrationTestBase;
import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.academic.SubjectRepository;
import com.school.audit.AuditLogRepository;
import com.school.student.Gender;
import com.school.student.Student;
import com.school.student.StudentRepository;
import com.school.student.StudentStatus;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole application runs on a clock fixed at 00:30 on 26 Nov 2026 in Dhaka (still 25 Nov in UTC),
 * the hour in which the old prototype wrongly called "today" a future date.
 */
class AttendanceIntegrationTest extends IntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedDhakaClock() {
            return Clock.fixed(Instant.parse("2026-11-25T18:30:00Z"), ZoneId.of("Asia/Dhaka"));
        }
    }

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private ClassSubjectRepository classSubjectRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private SchoolClass class9A;
    private Student amina;
    private Student babul;
    private Student leftSchool;
    private String adminToken;
    private String classTeacherToken;
    private String subjectTeacherToken;
    private String otherTeacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN));
        Teacher classTeacher = teacher("t-1001", "T-1001", "Ayesha");
        Teacher subjectTeacher = teacher("t-1002", "T-1002", "Karim");
        Teacher otherTeacher = teacher("t-1003", "T-1003", "Rina");
        appUserRepository.save(new AppUser(
                "adm-2026-001", "amina@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam",
                Role.STUDENT));

        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);

        class9A = schoolClassRepository.save(new SchoolClass(year, "Class 9", "A", "101", 40, classTeacher));
        SchoolClass class9B = schoolClassRepository.save(new SchoolClass(year, "Class 9", "B", "102", 40, otherTeacher));
        Subject math = subjectRepository.save(new Subject("MATH", "Mathematics", 100, 33));
        classSubjectRepository.save(new ClassSubject(class9A, math, subjectTeacher));
        classSubjectRepository.save(new ClassSubject(class9B, math, otherTeacher));

        babul = student("ADM-2026-002", "Babul", 2, StudentStatus.ACTIVE);
        amina = student("ADM-2026-001", "Amina", 1, StudentStatus.ACTIVE);
        leftSchool = student("ADM-2026-003", "Chandni", 3, StudentStatus.TRANSFERRED);
        appUserRepository.flush();

        adminToken = login("admin", "AdminPass#1");
        classTeacherToken = login("t-1001", "TeacherPass#1");
        subjectTeacherToken = login("t-1002", "TeacherPass#1");
        otherTeacherToken = login("t-1003", "TeacherPass#1");
        studentToken = login("adm-2026-001", "StudentPass#1");
    }

    @Test
    void getSheet_notTaken_listsActiveStudentsByRoll_withoutStatus() throws Exception {
        getSheet(classTeacherToken, class9A.getId(), TODAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("Class 9 - A"))
                .andExpect(jsonPath("$.date").value("2026-11-26"))
                .andExpect(jsonPath("$.taken").value(false))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].studentId").value(amina.getId()))
                .andExpect(jsonPath("$.rows[0].rollNo").value(1))
                .andExpect(jsonPath("$.rows[0].studentName").value("Amina Islam"))
                .andExpect(jsonPath("$.rows[0].status").doesNotExist())
                .andExpect(jsonPath("$.rows[1].studentId").value(babul.getId()));
    }

    @Test
    void save_thenSaveAgain_correctsTheDay_andAudits() throws Exception {
        saveSheet(classTeacherToken, TODAY, List.of(
                entry(amina, "PRESENT", null),
                entry(babul, "ABSENT", "Fever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(true))
                .andExpect(jsonPath("$.rows[0].status").value("PRESENT"))
                .andExpect(jsonPath("$.rows[1].status").value("ABSENT"))
                .andExpect(jsonPath("$.rows[1].remarks").value("Fever"));

        // Correction: Babul came late after all. Still one row per student and day.
        saveSheet(adminToken, TODAY, List.of(
                entry(amina, "PRESENT", null),
                entry(babul, "LATE", "Came at 10:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[1].status").value("LATE"))
                .andExpect(jsonPath("$.rows[1].remarks").value("Came at 10:00"));

        getSheet(classTeacherToken, class9A.getId(), TODAY)
                .andExpect(jsonPath("$.taken").value(true))
                .andExpect(jsonPath("$.rows[1].status").value("LATE"));
        assertThat(attendanceRecordRepository.count()).isEqualTo(2);
        assertThat(auditLogRepository.findAll()).extracting("action")
                .filteredOn("ATTENDANCE_SAVED"::equals).hasSize(2);
    }

    @Test
    void save_oneBadRow_savesNothing() throws Exception {
        saveSheet(classTeacherToken, TODAY, List.of(
                entry(amina, "PRESENT", null),
                entry(leftSchool, "PRESENT", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value("Student " + leftSchool.getId() + " is not an active student of this class"));

        assertThat(attendanceRecordRepository.count()).isZero();
    }

    @Test
    void save_duplicateStudent_is422() throws Exception {
        saveSheet(classTeacherToken, TODAY, List.of(
                entry(amina, "PRESENT", null),
                entry(amina, "ABSENT", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Duplicate student " + amina.getId() + " in attendance sheet"));
        assertThat(attendanceRecordRepository.count()).isZero();
    }

    @Test
    void save_todayAt0030Dhaka_isAllowed_butTomorrowIsFuture() throws Exception {
        saveSheet(classTeacherToken, TODAY, List.of(entry(amina, "PRESENT", null)))
                .andExpect(status().isOk());

        saveSheet(classTeacherToken, TODAY.plusDays(1), List.of(entry(amina, "PRESENT", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Attendance cannot be taken for a future date"));
    }

    @Test
    void save_academicYearBoundaries() throws Exception {
        saveSheet(classTeacherToken, LocalDate.of(2026, 1, 1), List.of(entry(amina, "PRESENT", null)))
                .andExpect(status().isOk());

        saveSheet(classTeacherToken, LocalDate.of(2025, 12, 31), List.of(entry(amina, "PRESENT", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("The date is outside academic year 2026"));
    }

    @Test
    void subjectTeacherOfTheClass_isAllowed() throws Exception {
        getSheet(subjectTeacherToken, class9A.getId(), TODAY).andExpect(status().isOk());
        saveSheet(subjectTeacherToken, TODAY, List.of(entry(amina, "EXCUSED", "Sports day")))
                .andExpect(status().isOk());
    }

    @Test
    void teacherOfAnotherClass_isDenied() throws Exception {
        getSheet(otherTeacherToken, class9A.getId(), TODAY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You do not teach in this class"));
        saveSheet(otherTeacherToken, TODAY, List.of(entry(amina, "PRESENT", null)))
                .andExpect(status().isForbidden());
        assertThat(attendanceRecordRepository.count()).isZero();
    }

    @Test
    void studentAndAnonymous_areDenied() throws Exception {
        getSheet(studentToken, class9A.getId(), TODAY).andExpect(status().isForbidden());
        saveSheet(studentToken, TODAY, List.of(entry(amina, "PRESENT", null))).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/attendance/sheet")
                        .param("classId", class9A.getId().toString())
                        .param("date", TODAY.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownClass_is404() throws Exception {
        getSheet(adminToken, 999_999L, TODAY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Class 999999 was not found"));
    }

    @Test
    void badInput_is400_notA500() throws Exception {
        // Missing status
        mockMvc.perform(put("/api/v1/attendance/sheet")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "classId", class9A.getId(), "date", TODAY.toString(),
                                "entries", List.of(Map.of("studentId", amina.getId()))))))
                .andExpect(status().isBadRequest());
        // Empty entries
        saveSheet(adminToken, TODAY, List.of()).andExpect(status().isBadRequest());
        // Unknown status value
        saveSheet(adminToken, TODAY, List.of(entry(amina, "HOLIDAY", null))).andExpect(status().isBadRequest());
        // Remarks too long
        saveSheet(adminToken, TODAY, List.of(entry(amina, "PRESENT", "x".repeat(256))))
                .andExpect(status().isBadRequest());
        // Not a date
        mockMvc.perform(get("/api/v1/attendance/sheet")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("classId", class9A.getId().toString())
                        .param("date", "26-11-2026"))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private ResultActions getSheet(String token, Long classId, LocalDate date) throws Exception {
        return mockMvc.perform(get("/api/v1/attendance/sheet")
                .header("Authorization", "Bearer " + token)
                .param("classId", classId.toString())
                .param("date", date.toString()));
    }

    private ResultActions saveSheet(String token, LocalDate date, List<Map<String, Object>> entries)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(), "date", date.toString(), "entries", entries));
        return mockMvc.perform(put("/api/v1/attendance/sheet")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Map<String, Object> entry(Student student, String status, String remarks) {
        return remarks == null
                ? Map.of("studentId", student.getId(), "status", status)
                : Map.of("studentId", student.getId(), "status", status, "remarks", remarks);
    }

    private Teacher teacher(String username, String employeeNo, String firstName) {
        AppUser user = appUserRepository.save(new AppUser(
                username, username + "@school.edu", passwordEncoder.encode("TeacherPass#1"),
                firstName + " Rahman", Role.TEACHER));
        return teacherRepository.save(new Teacher(
                user, employeeNo, firstName, "Rahman", username + "@school.edu", "01711111111", "Teacher",
                LocalDate.of(2024, 1, 1)));
    }

    private Student student(String admissionNo, String firstName, int roll, StudentStatus status) {
        Student student = new Student(admissionNo);
        student.setFirstName(firstName);
        student.setLastName(firstName.equals("Amina") ? "Islam" : "Hossain");
        student.setGender(Gender.FEMALE);
        student.setDateOfBirth(LocalDate.of(2011, 5, 10));
        student.setGuardianName("Parent " + firstName);
        student.setGuardianPhone("0170000000" + roll);
        student.setGuardianRelation("Father");
        student.setAdmissionDate(LocalDate.of(2026, 1, 5));
        student.setSchoolClass(class9A);
        student.setRollNo(roll);
        student.setStatus(status);
        return studentRepository.save(student);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
