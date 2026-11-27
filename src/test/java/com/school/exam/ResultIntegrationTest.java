package com.school.exam;

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
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class ResultIntegrationTest extends IntegrationTestBase {

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
    private ExamRepository examRepository;
    @Autowired
    private MarkRepository markRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApplicationEvents applicationEvents;

    private AcademicYear year;
    private SchoolClass class9A;
    private Subject math;
    private Subject eng;
    private Student s1;
    private Student s2;
    private Student s3;
    private Student s4;
    private Exam exam;
    private String adminToken;
    private String classTeacherToken;
    private String unrelatedTeacherToken;
    private String student1Token;

    @BeforeEach
    void setUp() throws Exception {
        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN
        ));
        AppUser classTeacherUser = appUserRepository.save(new AppUser(
                "t-1001", "ayesha@school.edu", passwordEncoder.encode("TeacherPass#1"), "Ayesha Rahman", Role.TEACHER
        ));
        AppUser unrelatedTeacherUser = appUserRepository.save(new AppUser(
                "t-1002", "karim@school.edu", passwordEncoder.encode("TeacherPass#2"), "Karim Uddin", Role.TEACHER
        ));
        AppUser student1User = appUserRepository.save(new AppUser(
                "adm-2026-001", "amina@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam", Role.STUDENT
        ));

        Teacher classTeacher = teacherRepository.save(new Teacher(
                classTeacherUser, "T-1001", "Ayesha", "Rahman", "ayesha@school.edu", "01711111111", "Senior", LocalDate.of(2024, 1, 1)
        ));
        teacherRepository.save(new Teacher(
                unrelatedTeacherUser, "T-1002", "Karim", "Uddin", "karim@school.edu", "01722222222", "Junior", LocalDate.of(2024, 1, 1)
        ));

        year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);

        class9A = schoolClassRepository.save(new SchoolClass(year, "Class 9", "A", "101", 40, classTeacher));
        math = subjectRepository.save(new Subject("MATH", "Mathematics", 100, 33));
        eng = subjectRepository.save(new Subject("ENG", "English", 100, 33));
        classSubjectRepository.save(new ClassSubject(class9A, math, classTeacher));
        classSubjectRepository.save(new ClassSubject(class9A, eng, classTeacher));

        s1 = createStudent("ADM-2026-001", "Amina", "Islam", 1, class9A, student1User);
        s2 = createStudent("ADM-2026-002", "Babul", "Hossain", 2, class9A, null);
        s3 = createStudent("ADM-2026-003", "Champa", "Begum", 3, class9A, null);
        s4 = createStudent("ADM-2026-004", "Dipu", "Roy", 4, class9A, null);

        exam = examRepository.save(new Exam(year, "Annual Examination", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)));

        appUserRepository.flush();
        examRepository.flush();

        adminToken = loginAndGetToken("admin", "AdminPass#1");
        classTeacherToken = loginAndGetToken("t-1001", "TeacherPass#1");
        unrelatedTeacherToken = loginAndGetToken("t-1002", "TeacherPass#2");
        student1Token = loginAndGetToken("adm-2026-001", "StudentPass#1");
    }

    @Test
    void publishLifecycle_rejectsEmptyAndHalfMarked_publishesComplete_ranksTies_andHidesDraftsFromStudents() throws Exception {
        // 1. Empty exam (0 marks) -> 422
        mockMvc.perform(post("/api/v1/exams/{id}/publish", exam.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("Class 9 - A / MATH")));

        // 2. Half-marked exam (MATH entered for all, ENG missing) -> 422 listing ENG
        markRepository.saveAllAndFlush(List.of(
                new Mark(exam, s1, math, new BigDecimal("90"), false),
                new Mark(exam, s2, math, new BigDecimal("90"), false),
                new Mark(exam, s3, math, new BigDecimal("70"), false),
                new Mark(exam, s4, math, new BigDecimal("20"), false)
        ));

        mockMvc.perform(post("/api/v1/exams/{id}/publish", exam.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("Class 9 - A / ENG")));

        // Student never sees DRAFT exams
        mockMvc.perform(get("/api/v1/me/results")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/me/results/{examId}", exam.getId())
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No published result found for this exam"));

        // 3. Complete all marks (s1 & s2 tie for #1, s3 is #3, s4 fails with no position)
        markRepository.saveAllAndFlush(List.of(
                new Mark(exam, s1, eng, new BigDecimal("85"), false),
                new Mark(exam, s2, eng, new BigDecimal("85"), false),
                new Mark(exam, s3, eng, new BigDecimal("72"), false),
                new Mark(exam, s4, eng, null, true)
        ));

        mockMvc.perform(post("/api/v1/exams/{id}/publish", exam.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        assertThat(applicationEvents.stream(ResultsPublishedEvent.class).toList())
                .containsExactly(new ResultsPublishedEvent(exam.getId(), "Annual Examination"));

        // Class result for assigned teacher -> 200 with ties "1, 1, 3" and failed unranked at end
        mockMvc.perform(get("/api/v1/exams/{id}/results", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .header("Authorization", "Bearer " + classTeacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(3))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.students[0].position").value(1))
                .andExpect(jsonPath("$.students[1].position").value(1))
                .andExpect(jsonPath("$.students[2].position").value(3))
                .andExpect(jsonPath("$.students[3].passed").value(false))
                .andExpect(jsonPath("$.students[3].position").doesNotExist());

        // Student sees published exam in /me/results and /me/results/{examId}
        mockMvc.perform(get("/api/v1/me/results")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].examId").value(exam.getId()))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].gpa").value(5.0))
                .andExpect(jsonPath("$[0].letter").value("A+"));

        mockMvc.perform(get("/api/v1/me/results/{examId}", exam.getId())
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(s1.getId()))
                .andExpect(jsonPath("$.subjects.length()").value(2));

        // 4. Unpublish -> DRAFT again, student no longer sees it
        mockMvc.perform(post("/api/v1/exams/{id}/unpublish", exam.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/me/results/{examId}", exam.getId())
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No published result found for this exam"));

        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("RESULTS_PUBLISHED", "RESULTS_UNPUBLISHED");
    }

    @Test
    void roleAndOwnershipRules_enforcedAcrossAllResultEndpoints() throws Exception {
        // Unrelated teacher gets 403 on class result
        mockMvc.perform(get("/api/v1/exams/{id}/results", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .header("Authorization", "Bearer " + unrelatedTeacherToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Missing class -> 404 exact message
        mockMvc.perform(get("/api/v1/exams/{id}/results", exam.getId())
                        .param("classId", "999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Class 999999 was not found"));

        // Non-admins denied publish/unpublish (403)
        for (String token : List.of(classTeacherToken, student1Token)) {
            mockMvc.perform(post("/api/v1/exams/{id}/publish", exam.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/exams/{id}/unpublish", exam.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        // Student denied class result; admin & teacher denied /me/results (403)
        mockMvc.perform(get("/api/v1/exams/{id}/results", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/me/results")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/results")
                        .header("Authorization", "Bearer " + classTeacherToken))
                .andExpect(status().isForbidden());

        // Anonymous -> 401
        mockMvc.perform(get("/api/v1/me/results"))
                .andExpect(status().isUnauthorized());
    }

    private Student createStudent(String admNo, String first, String last, int roll, SchoolClass schoolClass, AppUser user) {
        Student s = new Student(admNo);
        s.setFirstName(first);
        s.setLastName(last);
        s.setGender(Gender.FEMALE);
        s.setDateOfBirth(LocalDate.of(2011, 5, 10));
        s.setGuardianName("Parent " + first);
        s.setGuardianPhone("0170000000" + roll);
        s.setGuardianRelation("Father");
        s.setAdmissionDate(LocalDate.of(2026, 1, 5));
        s.setSchoolClass(schoolClass);
        s.setRollNo(roll);
        s.setUser(user);
        return studentRepository.save(s);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
