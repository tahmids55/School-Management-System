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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarkIntegrationTest extends IntegrationTestBase {

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

    private Exam exam;
    private SchoolClass class9A;
    private Subject math;
    private Subject unassignedSubject;
    private Student student1;
    private Student student2;
    private String adminToken;
    private String assignedTeacherToken;
    private String otherTeacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN
        ));
        AppUser assignedTeacherUser = appUserRepository.save(new AppUser(
                "t-1001", "ayesha@school.edu", passwordEncoder.encode("TeacherPass#1"), "Ayesha Rahman", Role.TEACHER
        ));
        AppUser otherTeacherUser = appUserRepository.save(new AppUser(
                "t-1002", "karim@school.edu", passwordEncoder.encode("TeacherPass#2"), "Karim Uddin", Role.TEACHER
        ));
        appUserRepository.save(new AppUser(
                "adm-2026-001", "amina@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam", Role.STUDENT
        ));

        Teacher assignedTeacher = teacherRepository.save(new Teacher(
                assignedTeacherUser, "T-1001", "Ayesha", "Rahman", "ayesha@school.edu", "01711111111", "Math Teacher", LocalDate.of(2024, 1, 1)
        ));

        Teacher otherTeacher = teacherRepository.save(new Teacher(
                otherTeacherUser, "T-1002", "Karim", "Uddin", "karim@school.edu", "01722222222", "English Teacher", LocalDate.of(2024, 1, 1)
        ));

        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);

        // Make otherTeacher the class teacher to prove class-teacher status alone does NOT grant subject grading!
        class9A = schoolClassRepository.save(new SchoolClass(year, "Class 9", "A", "101", 40, otherTeacher));

        math = subjectRepository.save(new Subject("MATH", "Mathematics", 100, 33));
        unassignedSubject = subjectRepository.save(new Subject("PHY", "Physics", 100, 33));

        classSubjectRepository.save(new ClassSubject(class9A, math, assignedTeacher));

        student1 = createStudent("ADM-2026-001", "Amina", "Islam", 1, class9A);
        student2 = createStudent("ADM-2026-002", "Babul", "Hossain", 2, class9A);

        exam = examRepository.save(new Exam(year, "Annual Exam", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)));

        appUserRepository.flush();
        examRepository.flush();

        adminToken = loginAndGetToken("admin", "AdminPass#1");
        assignedTeacherToken = loginAndGetToken("t-1001", "TeacherPass#1");
        otherTeacherToken = loginAndGetToken("t-1002", "TeacherPass#2");
        studentToken = loginAndGetToken("adm-2026-001", "StudentPass#1");
    }

    @Test
    void assignedTeacherAndAdmin_saveMarks_0FullAbsent_andAudit() throws Exception {
        // Assigned teacher fetches empty sheet (ordered by roll)
        mockMvc.perform(get("/api/v1/exams/{id}/marks", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .param("subjectId", math.getId().toString())
                        .header("Authorization", "Bearer " + assignedTeacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.fullMarks").value(100))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].studentId").value(student1.getId()))
                .andExpect(jsonPath("$.rows[1].studentId").value(student2.getId()));

        // Assigned teacher saves 0 and full marks (100)
        String body = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(
                        Map.of("studentId", student1.getId(), "marksObtained", 0, "absent", false),
                        Map.of("studentId", student2.getId(), "marksObtained", 100, "absent", false)
                )
        ));

        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + assignedTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].marksObtained").value(0))
                .andExpect(jsonPath("$.rows[1].marksObtained").value(100));

        // Admin updates row 2 to absent (marksObtained becomes null and omitted from JSON)
        String absentBody = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(
                        Map.of("studentId", student1.getId(), "marksObtained", new BigDecimal("78.50"), "absent", false),
                        Map.of("studentId", student2.getId(), "absent", true)
                )
        ));

        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(absentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].marksObtained").value(78.5))
                .andExpect(jsonPath("$.rows[1].absent").value(true))
                .andExpect(jsonPath("$.rows[1].marksObtained").doesNotExist());

        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("MARKS_SAVED");
    }

    @Test
    void oneBadRow_savesNothing_andBoundaryValidationsEnforced() throws Exception {
        // Row 1 is valid (80), Row 2 is full + 0.01 (100.01) -> 422 and 0 rows saved!
        String oneBadRow = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(
                        Map.of("studentId", student1.getId(), "marksObtained", 80, "absent", false),
                        Map.of("studentId", student2.getId(), "marksObtained", new BigDecimal("100.01"), "absent", false)
                )
        ));

        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oneBadRow))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Marks must be between 0 and 100 for MATH"));

        assertThat(markRepository.count()).isZero();

        // Negative marks -> 400 (Bean Validation @DecimalMin("0.0"))
        String negativeBody = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(Map.of("studentId", student1.getId(), "marksObtained", -1, "absent", false))
        ));
        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(negativeBody))
                .andExpect(status().isBadRequest());

        // Missing marks when not absent -> 422
        String missingBody = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(Map.of("studentId", student1.getId(), "absent", false))
        ));
        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Enter marks or mark the student absent (student " + student1.getId() + ")"));

        // Subject not taught in class -> 404
        mockMvc.perform(get("/api/v1/exams/{id}/marks", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .param("subjectId", unassignedSubject.getId().toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("This subject is not taught in the selected class"));
    }

    @Test
    void publishedExam_returns409OnSave_andUnassignedTeacherOrStudentGets403() throws Exception {
        // Other teacher (even class teacher!) denied on GET and PUT -> 403
        mockMvc.perform(get("/api/v1/exams/{id}/marks", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .param("subjectId", math.getId().toString())
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String validBody = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(Map.of("studentId", student1.getId(), "marksObtained", 75, "absent", false))
        ));
        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        // Student denied -> 403; anonymous -> 401
        mockMvc.perform(get("/api/v1/exams/{id}/marks", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .param("subjectId", math.getId().toString())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/exams/{id}/marks", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .param("subjectId", math.getId().toString()))
                .andExpect(status().isUnauthorized());

        // Published exam: GET shows editable=false, PUT returns 409
        exam.publish(Instant.parse("2026-11-20T06:00:00Z"));
        examRepository.saveAndFlush(exam);

        mockMvc.perform(get("/api/v1/exams/{id}/marks", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .param("subjectId", math.getId().toString())
                        .header("Authorization", "Bearer " + assignedTeacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));

        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Results of Annual Exam are published. Unpublish them before making changes."));
    }

    private Student createStudent(String admNo, String first, String last, int roll, SchoolClass schoolClass) {
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
