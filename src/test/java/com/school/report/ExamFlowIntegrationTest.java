package com.school.report;

import com.school.IntegrationTestBase;
import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.academic.SubjectRepository;
import com.school.exam.Exam;
import com.school.exam.ExamRepository;
import com.school.exam.MarkRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamFlowIntegrationTest extends IntegrationTestBase {

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
    private PasswordEncoder passwordEncoder;

    private SchoolClass class9A;
    private Subject math;
    private Student student1;
    private Student student2;
    private Exam exam;
    private String adminToken;
    private String assignedTeacherToken;
    private String otherTeacherToken;
    private String student1Token;

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
        AppUser student1User = appUserRepository.save(new AppUser(
                "adm-2026-001", "amina@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam", Role.STUDENT
        ));

        Teacher assignedTeacher = teacherRepository.save(new Teacher(
                assignedTeacherUser, "T-1001", "Ayesha", "Rahman", "ayesha@school.edu", "01711111111", "Math", LocalDate.of(2024, 1, 1)
        ));
        teacherRepository.save(new Teacher(
                otherTeacherUser, "T-1002", "Karim", "Uddin", "karim@school.edu", "01722222222", "Other", LocalDate.of(2024, 1, 1)
        ));

        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);

        class9A = schoolClassRepository.save(new SchoolClass(year, "Class 9", "A", "101", 40, assignedTeacher));
        math = subjectRepository.save(new Subject("MATH", "Mathematics", 100, 33));
        classSubjectRepository.save(new ClassSubject(class9A, math, assignedTeacher));

        student1 = createStudent("ADM-2026-001", "Amina", "Islam", 1, class9A, student1User);
        student2 = createStudent("ADM-2026-002", "Babul", "Hossain", 2, class9A, null);

        exam = examRepository.save(new Exam(year, "Annual Exam", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)));

        appUserRepository.flush();
        examRepository.flush();

        adminToken = loginAndGetToken("admin", "AdminPass#1");
        assignedTeacherToken = loginAndGetToken("t-1001", "TeacherPass#1");
        otherTeacherToken = loginAndGetToken("t-1002", "TeacherPass#2");
        student1Token = loginAndGetToken("adm-2026-001", "StudentPass#1");
    }

    @Test
    void assignedTeacher_entersMarks_andClassResultIsRanked() throws Exception {
        saveMarksAs(assignedTeacherToken, 88, 95);

        mockMvc.perform(get("/api/v1/exams/{id}/results", exam.getId())
                        .param("classId", class9A.getId().toString())
                        .header("Authorization", "Bearer " + assignedTeacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(2))
                .andExpect(jsonPath("$.students[0].studentId").value(student2.getId()))
                .andExpect(jsonPath("$.students[0].position").value(1))
                .andExpect(jsonPath("$.students[1].studentId").value(student1.getId()))
                .andExpect(jsonPath("$.students[1].position").value(2));
    }

    @Test
    void teacherNotAssignedToTheSubject_gets403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(Map.of("studentId", student1.getId(), "marksObtained", 80, "absent", false))
        ));

        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        // Unrelated teacher also cannot download this student's report card -> 403
        mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", exam.getId(), student1.getId())
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void oneInvalidRow_rejectsTheWholeSheet() throws Exception {
        String badSheet = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(
                        Map.of("studentId", student1.getId(), "marksObtained", 80, "absent", false),
                        Map.of("studentId", student2.getId(), "marksObtained", new BigDecimal("101"), "absent", false)
                )
        ));

        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + assignedTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badSheet))
                .andExpect(status().isUnprocessableEntity());

        assertThat(markRepository.count()).isZero();
    }

    @Test
    void publishedExam_locksMarks() throws Exception {
        saveMarksAs(assignedTeacherToken, 85, 72);

        mockMvc.perform(post("/api/v1/exams/{id}/publish", exam.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        String body = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(Map.of("studentId", student1.getId(), "marksObtained", 90, "absent", false))
        ));

        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void student_cannotSeeDraftReportCard_butCanAfterPublishing() throws Exception {
        saveMarksAs(assignedTeacherToken, 88, 76);

        // Student cannot download draft report card -> 403 with exact message
        mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", exam.getId(), student1.getId())
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Results of this exam are not published yet"));

        // Assigned teacher and admin CAN download draft report card -> 200 PDF
        mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", exam.getId(), student1.getId())
                        .header("Authorization", "Bearer " + assignedTeacherToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", exam.getId(), student1.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // Publish exam
        mockMvc.perform(post("/api/v1/exams/{id}/publish", exam.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Student downloads PDF -> 200 application/pdf, %PDF, exact filename
        MvcResult pdfResult = mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", exam.getId(), student1.getId())
                        .param("format", "PDF")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        containsString("report-card-" + student1.getId() + "-" + exam.getId() + ".pdf")))
                .andReturn();
        assertThat(new String(pdfResult.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");

        // Student downloads CSV -> 200 text/csv, exact filename & content
        MvcResult csvResult = mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", exam.getId(), student1.getId())
                        .param("format", "CSV")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        containsString("report-card-" + student1.getId() + "-" + exam.getId() + ".csv")))
                .andReturn();
        assertThat(csvResult.getResponse().getContentAsString())
                .contains("\"Student\",\"Amina Islam\"")
                .contains("\"Position\",\"1 of 2\"");
    }

    @Test
    void student_cannotDownloadAnotherStudentsReportCard() throws Exception {
        saveMarksAs(assignedTeacherToken, 88, 76);
        mockMvc.perform(post("/api/v1/exams/{id}/publish", exam.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", exam.getId(), student2.getId())
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Anonymous rejected -> 401
        mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", exam.getId(), student1.getId()))
                .andExpect(status().isUnauthorized());
    }

    private void saveMarksAs(String token, int marks1, int marks2) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "subjectId", math.getId(),
                "entries", List.of(
                        Map.of("studentId", student1.getId(), "marksObtained", marks1, "absent", false),
                        Map.of("studentId", student2.getId(), "marksObtained", marks2, "absent", false)
                )
        ));
        mockMvc.perform(put("/api/v1/exams/{id}/marks", exam.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
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
