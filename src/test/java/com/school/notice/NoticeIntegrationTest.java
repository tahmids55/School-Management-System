package com.school.notice;

import com.school.IntegrationTestBase;
import com.school.audit.AuditLogRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class NoticeIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private ApplicationEvents applicationEvents;

    private String adminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUpUsersAndTokens() throws Exception {
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
    }

    @Test
    void listAndGetById_enforceAudienceRulesAndPinnedNewestOrder() throws Exception {
        Instant now = clock.instant();
        Notice olderAll = noticeRepository.save(
                new Notice("General Older", "Body 1", NoticeAudience.ALL, false, now.minusSeconds(300))
        );
        Notice newerTeacher = noticeRepository.save(
                new Notice("Teacher Update", "Body 2", NoticeAudience.TEACHERS, false, now.minusSeconds(100))
        );
        Notice pinnedStudent = noticeRepository.save(
                new Notice("Student Exam", "Body 3", NoticeAudience.STUDENTS, true, now.minusSeconds(600))
        );
        noticeRepository.flush();

        // ADMIN sees all 3, pinned first then newest
        mockMvc.perform(get("/api/v1/notices")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(pinnedStudent.getId()))
                .andExpect(jsonPath("$.content[1].id").value(newerTeacher.getId()))
                .andExpect(jsonPath("$.content[2].id").value(olderAll.getId()));

        // TEACHER sees ALL + TEACHERS only
        mockMvc.perform(get("/api/v1/notices")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newerTeacher.getId()))
                .andExpect(jsonPath("$.content[1].id").value(olderAll.getId()));

        // STUDENT sees ALL + STUDENTS only, pinned first
        mockMvc.perform(get("/api/v1/notices")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(pinnedStudent.getId()))
                .andExpect(jsonPath("$.content[1].id").value(olderAll.getId()));

        // GET /notices/{id}: visible returns 200, non-visible returns 404 with exact message
        mockMvc.perform(get("/api/v1/notices/{id}", newerTeacher.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Teacher Update"));

        mockMvc.perform(get("/api/v1/notices/{id}", pinnedStudent.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Notice " + pinnedStudent.getId() + " was not found"));

        mockMvc.perform(get("/api/v1/notices/{id}", pinnedStudent.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Student Exam"));

        mockMvc.perform(get("/api/v1/notices/{id}", newerTeacher.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Notice " + newerTeacher.getId() + " was not found"));

        mockMvc.perform(get("/api/v1/notices/{id}", pinnedStudent.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Anonymous rejected with 401
        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/notices/{id}", olderAll.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void crud_adminCreatesUpdatesDeletes_publishesEventAndAudits() throws Exception {
        String createPayload = objectMapper.writeValueAsString(Map.of(
                "title", "Winter Break",
                "body", "School is closed.\nClasses resume Jan 5.",
                "audience", "ALL",
                "pinned", true
        ));

        MvcResult createResult = mockMvc.perform(post("/api/v1/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Winter Break"))
                .andExpect(jsonPath("$.body").value("School is closed.\nClasses resume Jan 5."))
                .andExpect(jsonPath("$.audience").value("ALL"))
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.author").value("admin"))
                .andReturn();

        long createdId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Domain event was published on create
        List<NoticePublishedEvent> events = applicationEvents.stream(NoticePublishedEvent.class).toList();
        assertThat(events).containsExactly(new NoticePublishedEvent(createdId, "Winter Break", NoticeAudience.ALL));

        // Audit record for create
        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo("NOTICE_PUBLISHED");
                    assertThat(entry.getEntityType()).isEqualTo("Notice");
                    assertThat(entry.getEntityId()).isEqualTo(String.valueOf(createdId));
                    assertThat(entry.getActor()).isEqualTo("admin");
                });

        // Update
        String updatePayload = objectMapper.writeValueAsString(Map.of(
                "title", "Winter Break Updated",
                "body", "Resumes Jan 6.",
                "audience", "STUDENTS",
                "pinned", false
        ));

        mockMvc.perform(put("/api/v1/notices/{id}", createdId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Winter Break Updated"))
                .andExpect(jsonPath("$.audience").value("STUDENTS"))
                .andExpect(jsonPath("$.pinned").value(false));

        // Delete
        mockMvc.perform(delete("/api/v1/notices/{id}", createdId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo("NOTICE_DELETED");
                    assertThat(entry.getEntityType()).isEqualTo("Notice");
                    assertThat(entry.getEntityId()).isEqualTo(String.valueOf(createdId));
                });

        mockMvc.perform(get("/api/v1/notices/{id}", createdId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notice " + createdId + " was not found"));
    }

    @Test
    void writeEndpoints_denyTeacherAndStudent_with403ProblemJson() throws Exception {
        Notice existing = noticeRepository.saveAndFlush(
                new Notice("Notice", "Body", NoticeAudience.ALL, false, clock.instant())
        );
        String payload = objectMapper.writeValueAsString(Map.of(
                "title", "Unauthorized",
                "body", "Body",
                "audience", "ALL",
                "pinned", false
        ));

        for (String token : List.of(teacherToken, studentToken)) {
            mockMvc.perform(post("/api/v1/notices")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            mockMvc.perform(put("/api/v1/notices/{id}", existing.getId())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            mockMvc.perform(delete("/api/v1/notices/{id}", existing.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void validationAndUnknownInputs_return400Or404ProblemJson_never500() throws Exception {
        mockMvc.perform(post("/api/v1/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"body\":\"\",\"audience\":null,\"pinned\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.title").isNotEmpty())
                .andExpect(jsonPath("$.errors.body").isNotEmpty())
                .andExpect(jsonPath("$.errors.audience").isNotEmpty());

        mockMvc.perform(post("/api/v1/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hi\",\"body\":\"Body\",\"audience\":\"UNKNOWN\",\"pinned\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/v1/notices?sort=nonExistentField,desc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(put("/api/v1/notices/999999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hi\",\"body\":\"Body\",\"audience\":\"ALL\",\"pinned\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notice 999999 was not found"));

        mockMvc.perform(delete("/api/v1/notices/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notice 999999 was not found"));
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
