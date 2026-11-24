package com.school.audit;

import com.school.IntegrationTestBase;
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
import java.util.Map;

import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

        adminToken = loginAndGetToken("admin", "AdminPass#1");
        teacherToken = loginAndGetToken("t-1001", "TeacherPass#1");
        studentToken = loginAndGetToken("adm-2026-001", "StudentPass#1");

        // Clear login audit entries created during setup so assertions are deterministic
        auditLogRepository.deleteAll();
        auditLogRepository.flush();
    }

    @Test
    void search_filtersByActorIgnoringCase_exactAction_andPaginatesNewestFirst() throws Exception {
        Instant base = Instant.parse("2026-11-24T08:00:00Z");
        AuditLog oldest = auditLogRepository.save(
                new AuditLog("admin", "NOTICE_PUBLISHED", "Notice", "1", "First notice", base)
        );
        AuditLog middle = auditLogRepository.save(
                new AuditLog("admin2", "NOTICE_PUBLISHED", "Notice", "2", "Other admin", base.plusSeconds(60))
        );
        AuditLog newest = auditLogRepository.save(
                new AuditLog("admin", "NOTICE_DELETED", "Notice", "1", "Deleted notice", base.plusSeconds(120))
        );
        auditLogRepository.flush();

        // Default page size 25, newest first
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.content[1].id").value(middle.getId()))
                .andExpect(jsonPath("$.content[2].id").value(oldest.getId()));

        // Exact actor ignoring case ("ADMIN" matches "admin", not "admin2")
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("actor", "ADMIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.content[1].id").value(oldest.getId()));

        // Exact action
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("action", "NOTICE_PUBLISHED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(middle.getId()))
                .andExpect(jsonPath("$.content[1].id").value(oldest.getId()));

        // Combined actor + action
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("actor", "admin")
                        .param("action", "NOTICE_PUBLISHED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(oldest.getId()));

        // Pagination
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(oldest.getId()));
    }

    @Test
    void truncationAt50And1000_persistsAndReturnsCleanly() throws Exception {
        String longActor = "u".repeat(80);
        String longAction = "A".repeat(80);
        String longEntity = "E".repeat(80);
        String longId = "9".repeat(80);
        String longDetails = "x".repeat(1300);

        auditService.recordAs(longActor, longAction, longEntity, longId, longDetails);
        auditLogRepository.flush();

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actor", hasLength(50)))
                .andExpect(jsonPath("$.content[0].action", hasLength(50)))
                .andExpect(jsonPath("$.content[0].entityType", hasLength(50)))
                .andExpect(jsonPath("$.content[0].entityId", hasLength(50)))
                .andExpect(jsonPath("$.content[0].details", hasLength(1000)));
    }

    @Test
    void roleRules_andReadOnlyAndUnknownSort_enforced() throws Exception {
        // TEACHER and STUDENT denied with 403 problem+json
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Unauthenticated denied with 401 problem+json
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Read-only: no write endpoints exist
        mockMvc.perform(post("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/audit-logs/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/audit-logs/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Unknown sort field -> 400 problem+json (never 500)
        mockMvc.perform(get("/api/v1/audit-logs?sort=nonExistent,desc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
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
