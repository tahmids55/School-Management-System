package com.school.user;

import com.school.IntegrationTestBase;
import com.school.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private AppUser admin;
    private AppUser teacher;
    private AppUser student;
    private String adminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        admin = appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN
        ));
        teacher = appUserRepository.save(new AppUser(
                "t-1001", "ayesha@school.edu", passwordEncoder.encode("TeacherPass#1"), "Ayesha Rahman", Role.TEACHER
        ));
        student = appUserRepository.save(new AppUser(
                "adm-2026-001", "amina@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam", Role.STUDENT
        ));
        appUserRepository.flush();

        adminToken = loginAndGetToken("admin", "AdminPass#1");
        teacherToken = loginAndGetToken("t-1001", "TeacherPass#1");
        studentToken = loginAndGetToken("adm-2026-001", "StudentPass#1");
    }

    @Test
    void list_searchesByUsernameNameEmailAndRole_sortedByUsername_andRejectsUnknownSort() throws Exception {
        // Default size 20, sorted by username ascending: adm-2026-001, admin, t-1001
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].username").value("adm-2026-001"))
                .andExpect(jsonPath("$.content[1].username").value("admin"))
                .andExpect(jsonPath("$.content[2].username").value("t-1001"))
                .andExpect(jsonPath("$.content[0].lastLoginAt").isNotEmpty());

        // Search by full name
        mockMvc.perform(get("/api/v1/users")
                        .param("q", "rahman")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("t-1001"));

        // Search by email + role filter
        mockMvc.perform(get("/api/v1/users")
                        .param("q", "amina@school")
                        .param("role", "STUDENT")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("adm-2026-001"));

        // Unknown sort field -> 400 problem+json (never 500)
        mockMvc.perform(get("/api/v1/users?sort=passwordHash,asc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void disable_immediatelyRevokesExistingAccessTokenWith401_andSelfDisableReturns422() throws Exception {
        // Teacher's existing access token works before disable
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        // Admin disables teacher
        mockMvc.perform(patch("/api/v1/users/{id}/status", teacher.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Existing access token stops working IMMEDIATELY (401 problem+json)
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Admin re-enables teacher
        mockMvc.perform(patch("/api/v1/users/{id}/status", teacher.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Admin cannot disable their own account -> 422 problem+json
        mockMvc.perform(patch("/api/v1/users/{id}/status", admin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You cannot disable your own account"));

        // Audit records exist for disable and enable
        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("ACCOUNT_DISABLED", "ACCOUNT_ENABLED");
    }

    @Test
    void unlockAndResetPassword_workAndAudit_andMissingUserReturns404() throws Exception {
        // Lock student account
        student.setFailedLoginAttempts(5);
        student.setLockedUntil(clock.instant().plusSeconds(900));
        appUserRepository.saveAndFlush(student);

        // Unlock clears lock and counter
        mockMvc.perform(post("/api/v1/users/{id}/unlock", student.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));

        AppUser reloaded = appUserRepository.findById(student.getId()).orElseThrow();
        assertThat(reloaded.getLockedUntil()).isNull();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();

        // Reset password returns temporary password and sets mustChangePassword=true
        MvcResult resetResult = mockMvc.perform(post("/api/v1/users/{id}/reset-password", student.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("adm-2026-001"))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn();

        String tempPassword = objectMapper.readTree(resetResult.getResponse().getContentAsString())
                .get("temporaryPassword").asText();

        // Student can log in with the new temporary password and mustChangePassword is true
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "adm-2026-001",
                                "password", tempPassword
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));

        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("ACCOUNT_UNLOCKED", "PASSWORD_RESET");

        // Missing user returns 404 with exact message
        mockMvc.perform(post("/api/v1/users/999999/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User 999999 was not found"));
        mockMvc.perform(patch("/api/v1/users/999999/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User 999999 was not found"));
    }

    @Test
    void nonAdminAndAnonymous_areDeniedOnAllFourEndpoints() throws Exception {
        for (String token : List.of(teacherToken, studentToken)) {
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mockMvc.perform(patch("/api/v1/users/{id}/status", student.getId())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false}"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mockMvc.perform(post("/api/v1/users/{id}/unlock", student.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mockMvc.perform(post("/api/v1/users/{id}/reset-password", student.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
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
