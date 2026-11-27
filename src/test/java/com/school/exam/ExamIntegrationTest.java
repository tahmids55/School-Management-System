package com.school.exam;

import com.school.IntegrationTestBase;
import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.audit.AuditLogRepository;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AcademicYear currentYear;
    private AcademicYear oldYear;
    private String adminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN
        ));
        appUserRepository.save(new AppUser(
                "t-1001", "ayesha@school.edu", passwordEncoder.encode("TeacherPass#1"), "Ayesha Rahman", Role.TEACHER
        ));
        appUserRepository.save(new AppUser(
                "adm-2026-001", "student@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam", Role.STUDENT
        ));

        currentYear = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        currentYear.setCurrent(true);
        currentYear = academicYearRepository.save(currentYear);

        oldYear = new AcademicYear("2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        oldYear.setCurrent(false);
        oldYear = academicYearRepository.save(oldYear);

        appUserRepository.flush();
        academicYearRepository.flush();

        adminToken = loginAndGetToken("admin", "AdminPass#1");
        teacherToken = loginAndGetToken("t-1001", "TeacherPass#1");
        studentToken = loginAndGetToken("adm-2026-001", "StudentPass#1");
    }

    @Test
    void fullCrud_newestStartFirst_duplicatesAndYearDeleteGuard() throws Exception {
        // Create two exams in current year
        MvcResult firstRes = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "  First Term  ", "2026-04-01", "2026-04-10")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("First Term"))
                .andExpect(jsonPath("$.academicYearName").value("2026"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        long firstId = objectMapper.readTree(firstRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult secondRes = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "Annual Examination", "2026-11-01", "2026-11-15")))
                .andExpect(status().isCreated())
                .andReturn();
        long secondId = objectMapper.readTree(secondRes.getResponse().getContentAsString()).get("id").asLong();

        // List defaults to current year, newest startDate first
        mockMvc.perform(get("/api/v1/exams")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[1].id").value(firstId));

        // Get by ID works for TEACHER
        mockMvc.perform(get("/api/v1/exams/{id}", firstId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("First Term"));

        // Duplicate name in same year -> 409
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "first term", "2026-05-01", "2026-05-05")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An exam named first term already exists in 2026"));

        // Same name allowed in another year, and prevents deleting that year (409)
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(oldYear.getId(), "First Term", "2025-04-01", "2025-04-01")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/academic-years/{id}", oldYear.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This academic year has classes or exams and cannot be deleted"));

        // Update draft exam
        mockMvc.perform(put("/api/v1/exams/{id}", firstId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "Midterm", "2026-06-01", "2026-06-10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Midterm"));

        // Update to duplicate name of another exam -> 409
        mockMvc.perform(put("/api/v1/exams/{id}", firstId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "annual examination", "2026-06-01", "2026-06-10")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An exam named annual examination already exists in 2026"));

        // Delete draft exam
        mockMvc.perform(delete("/api/v1/exams/{id}", firstId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("EXAM_CREATED", "EXAM_DELETED");
    }

    @Test
    void publishedExam_cannotBeUpdatedOrDeleted_returns409() throws Exception {
        Exam exam = new Exam(currentYear, "Final Exam", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
        exam.publish(Instant.parse("2026-11-20T06:00:00Z"));
        exam = examRepository.saveAndFlush(exam);

        mockMvc.perform(put("/api/v1/exams/{id}", exam.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "Renamed", "2026-11-01", "2026-11-12")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Results of Final Exam are published. Unpublish them before making changes."));

        mockMvc.perform(delete("/api/v1/exams/{id}", exam.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Results of Final Exam are published. Unpublish them before making changes."));
    }

    @Test
    void dateValidation_missingIds_andRoleRules() throws Exception {
        // End before start -> 422
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "Bad Dates", "2026-05-10", "2026-05-09")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("End date cannot be before start date"));

        // Outside year -> 422
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "Outside Year", "2026-12-20", "2027-01-02")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Exam dates must be inside academic year 2026"));

        // Validation 400
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYearId\":null,\"name\":\"\",\"startDate\":null,\"endDate\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Unknown exam 404
        mockMvc.perform(get("/api/v1/exams/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Exam 999999 was not found"));

        // TEACHER denied writes (403); STUDENT denied reads & writes (403); anonymous denied (401)
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(examJson(currentYear.getId(), "Teacher Exam", "2026-05-01", "2026-05-02")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/exams")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/exams"))
                .andExpect(status().isUnauthorized());
    }

    private String examJson(Long yearId, String name, String start, String end) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "academicYearId", yearId,
                "name", name,
                "startDate", start,
                "endDate", end
        ));
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
