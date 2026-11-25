package com.school.teacher;

import com.fasterxml.jackson.databind.JsonNode;
import com.school.IntegrationTestBase;
import com.school.audit.AuditLogRepository;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/v1/teachers";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUpUsers() {
        for (Role role : Role.values()) {
            String username = role.name().toLowerCase();
            appUserRepository.save(new AppUser(
                    username,
                    username + "@school.edu",
                    passwordEncoder.encode("SecretPass1"),
                    role.name() + " User",
                    role
            ));
        }
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(role.toLowerCase()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String teacherBody(
            String employeeNo,
            String firstName,
            String lastName,
            String email,
            String phone,
            String designation,
            String joiningDate
    ) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeNo", employeeNo);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("email", email);
        body.put("phone", phone);
        body.put("designation", designation);
        body.put("joiningDate", joiningDate);
        return objectMapper.writeValueAsString(body);
    }

    private ResultActions createTeacher(String employeeNo, String firstName, String lastName, String email)
            throws Exception {
        return mockMvc.perform(post(URL).with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(teacherBody(
                        employeeNo,
                        firstName,
                        lastName,
                        email,
                        "+8801711111111",
                        "Senior Teacher",
                        "2026-01-15"
                )));
    }

    @Test
    void createTeacher_createsLoginInSameTransaction_populatesProfileLookup_andEnforcesPasswordChange()
            throws Exception {
        MvcResult createResult = mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherBody(
                                " t-1007 ",
                                " Ayesha ",
                                " Rahman ",
                                "Ayesha@School.EDU",
                                "+8801711111111",
                                "Assistant Head",
                                "2026-01-15"
                        )))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.teacher.id").isNumber())
                .andExpect(jsonPath("$.teacher.employeeNo").value("T-1007"))
                .andExpect(jsonPath("$.teacher.firstName").value("Ayesha"))
                .andExpect(jsonPath("$.teacher.lastName").value("Rahman"))
                .andExpect(jsonPath("$.teacher.fullName").value("Ayesha Rahman"))
                .andExpect(jsonPath("$.teacher.email").value("ayesha@school.edu"))
                .andExpect(jsonPath("$.teacher.status").value("ACTIVE"))
                .andExpect(jsonPath("$.teacher.username").value("t-1007"))
                .andExpect(jsonPath("$.account.username").value("t-1007"))
                .andExpect(jsonPath("$.account.temporaryPassword").isNotEmpty())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long teacherId = created.get("teacher").get("id").asLong();
        String tempPassword = created.get("account").get("temporaryPassword").asText();

        assertThat(auditLogRepository.findAll())
                .extracting(entry -> entry.getAction())
                .contains("ACCOUNT_CREATED", "TEACHER_CREATED");

        // Newly created teacher can log in with employeeNo (lower-case) and temp password
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "t-1007",
                                "password", tempPassword
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.teacherId").value(teacherId))
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Forced password change blocks /teachers until changed
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Change your temporary password first"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", tempPassword,
                                "newPassword", "TeacherNew1"
                        ))))
                .andExpect(status().isNoContent());

        // Re-login with new password grants access to /teachers
        MvcResult secondLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "t-1007",
                                "password", "TeacherNew1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andExpect(jsonPath("$.user.teacherId").value(teacherId))
                .andReturn();

        String newToken = objectMapper.readTree(secondLogin.getResponse().getContentAsString())
                .get("accessToken").asText();
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void duplicates_return409WithExactMessages_andUpdateKeepsEmployeeNoImmutable() throws Exception {
        MvcResult firstResult = createTeacher("T-1001", "Ayesha", "Rahman", "ayesha@school.edu")
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .get("teacher").get("id").asLong();

        MvcResult secondResult = createTeacher("T-1002", "Karim", "Uddin", "karim@school.edu")
                .andExpect(status().isCreated())
                .andReturn();
        long secondId = objectMapper.readTree(secondResult.getResponse().getContentAsString())
                .get("teacher").get("id").asLong();

        createTeacher("t-1001", "Another", "Person", "another@school.edu")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Employee number T-1001 already exists"));

        createTeacher("T-1003", "Another", "Person", "AYESHA@school.edu")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Email ayesha@school.edu is already used by another teacher"));

        // Update cannot hijack another teacher's email
        mockMvc.perform(put(URL + "/" + secondId).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherBody(
                                "T-1002",
                                "Karim",
                                "Uddin",
                                "AYESHA@school.edu",
                                "01722222222",
                                "Lecturer",
                                "2026-01-15"
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email ayesha@school.edu is already used by another teacher"));

        // Update keeps employeeNo unchanged even if a different value is passed
        mockMvc.perform(put(URL + "/" + firstId).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherBody(
                                "T-9999",
                                "Ayesha",
                                "Begum",
                                "ayesha.begum@school.edu",
                                "+8801799999999",
                                "Head Teacher",
                                "2026-01-20"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNo").value("T-1001"))
                .andExpect(jsonPath("$.fullName").value("Ayesha Begum"))
                .andExpect(jsonPath("$.email").value("ayesha.begum@school.edu"))
                .andExpect(jsonPath("$.designation").value("Head Teacher"));
    }

    @Test
    void resigned_disablesLoginImmediately_blocksExistingTokenAndRefresh_andKeepsHistory() throws Exception {
        MvcResult createResult = createTeacher("T-1010", "Tariq", "Hasan", "tariq@school.edu")
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long teacherId = created.get("teacher").get("id").asLong();
        String tempPassword = created.get("account").get("temporaryPassword").asText();

        // Clear mustChangePassword directly in DB for concise session test
        AppUser teacherUser = appUserRepository.findByUsernameIgnoreCase("t-1010").orElseThrow();
        teacherUser.setMustChangePassword(false);
        appUserRepository.saveAndFlush(teacherUser);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "t-1010",
                                "password", tempPassword
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(get(URL + "/" + teacherId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // ON_LEAVE leaves login active
        mockMvc.perform(patch(URL + "/" + teacherId + "/status").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_LEAVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_LEAVE"));

        mockMvc.perform(get(URL + "/" + teacherId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // RESIGNED disables login, revokes refresh tokens, and preserves teacher record
        mockMvc.perform(patch(URL + "/" + teacherId + "/status").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESIGNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESIGNED"));

        mockMvc.perform(get(URL + "/" + teacherId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "t-1010",
                                "password", tempPassword
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());

        // History remains readable to admin
        mockMvc.perform(get(URL + "/" + teacherId).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESIGNED"));

        assertThat(auditLogRepository.findAll())
                .extracting(entry -> entry.getAction())
                .contains("TEACHER_STATUS_CHANGED");
    }

    @Test
    void searchAndPagination_filtersByFullNameEmployeeNoEmailAndStatus_andUsesFixedQueries() throws Exception {
        createTeacher("T-2001", "Zara", "Khan", "zara@school.edu").andExpect(status().isCreated());
        createTeacher("T-2002", "Aminul", "Islam", "aminul@school.edu").andExpect(status().isCreated());
        MvcResult third = createTeacher("T-2003", "Bina", "Das", "bina.das@school.edu")
                .andExpect(status().isCreated())
                .andReturn();
        long binaId = objectMapper.readTree(third.getResponse().getContentAsString())
                .get("teacher").get("id").asLong();
        mockMvc.perform(patch(URL + "/" + binaId + "/status").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_LEAVE\"}"))
                .andExpect(status().isOk());

        // Default sort is firstName ascending, default page size is 20
        mockMvc.perform(get(URL).with(as("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].firstName").value("Aminul"))
                .andExpect(jsonPath("$.content[1].firstName").value("Bina"))
                .andExpect(jsonPath("$.content[2].firstName").value("Zara"));

        // Search by full name
        mockMvc.perform(get(URL).param("q", "aminul islam").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeNo").value("T-2002"));

        // Search by employee no
        mockMvc.perform(get(URL).param("q", "t-2001").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Zara"));

        // Search by email + status filter
        mockMvc.perform(get(URL).param("q", "bina.das").param("status", "ON_LEAVE").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeNo").value("T-2003"));

        mockMvc.perform(get(URL).param("q", "bina.das").param("status", "ACTIVE").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Verify no N+1 queries on list
        long fewQueryCount = countQueriesOfList();
        createTeacher("T-2004", "Farhana", "Akter", "farhana@school.edu").andExpect(status().isCreated());
        createTeacher("T-2005", "Mahmud", "Hasan", "mahmud@school.edu").andExpect(status().isCreated());
        long manyQueryCount = countQueriesOfList();
        // 1 AccountStateFilter user query + 2 pagination queries (select with join fetch + count)
        assertThat(manyQueryCount).isEqualTo(fewQueryCount).isLessThanOrEqualTo(3);
    }

    private long countQueriesOfList() throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            mockMvc.perform(get(URL).with(as("ADMIN")))
                    .andExpect(status().isOk());
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void validationAndErrors_returnProblemJsonWithExpectedCodes() throws Exception {
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherBody(
                                "",
                                "",
                                "",
                                "not-an-email",
                                "123",
                                "",
                                LocalDate.now().plusDays(5).toString()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.employeeNo").isNotEmpty())
                .andExpect(jsonPath("$.errors.firstName").isNotEmpty())
                .andExpect(jsonPath("$.errors.lastName").isNotEmpty())
                .andExpect(jsonPath("$.errors.email").isNotEmpty())
                .andExpect(jsonPath("$.errors.phone").value("must be 10-15 digits, optionally starting with +"))
                .andExpect(jsonPath("$.errors.designation").isNotEmpty())
                .andExpect(jsonPath("$.errors.joiningDate").isNotEmpty());

        mockMvc.perform(get(URL + "/999999").with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Teacher 999999 was not found"));

        mockMvc.perform(get(URL).param("sort", "unknownField").with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid sort field: unknownField"));

        mockMvc.perform(get(URL).param("status", "UNKNOWN").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleRules_adminAndTeacherReads_adminOnlyWrites_noDelete() throws Exception {
        MvcResult created = createTeacher("T-3001", "Nadia", "Sultana", "nadia@school.edu")
                .andExpect(status().isCreated())
                .andReturn();
        long teacherId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("teacher").get("id").asLong();

        // Reads: ADMIN & TEACHER allowed, STUDENT 403, anonymous 401
        for (String role : new String[] {"ADMIN", "TEACHER"}) {
            mockMvc.perform(get(URL).with(as(role))).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/" + teacherId).with(as(role))).andExpect(status().isOk());
        }
        mockMvc.perform(get(URL).with(as("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get(URL + "/" + teacherId).with(as("STUDENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        // Writes: ADMIN allowed, TEACHER & STUDENT 403, anonymous 401
        String payload = teacherBody(
                "T-3002", "Sadia", "Islam", "sadia@school.edu", "01733333333", "Lecturer", "2026-01-15");
        for (String role : new String[] {"TEACHER", "STUDENT"}) {
            mockMvc.perform(post(URL).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put(URL + "/" + teacherId).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch(URL + "/" + teacherId + "/status").with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ON_LEAVE\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());

        // No DELETE endpoint exists
        mockMvc.perform(delete(URL + "/" + teacherId).with(as("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void teachingLoad_returnsClassTeacherAndSubjects_withRoleAndProfileChecks() throws Exception {
        MvcResult created = createTeacher("T-4001", "Rafiq", "Ahmed", "rafiq@school.edu")
                .andExpect(status().isCreated())
                .andReturn();
        long teacherId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("teacher").get("id").asLong();

        // Create academic year, two classes (one with teacher as classTeacher), and two subjects
        MvcResult yearRes = mockMvc.perform(post("/api/v1/academic-years").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long yearId = objectMapper.readTree(yearRes.getResponse().getContentAsString()).get("id").asLong();

        MvcResult class1Res = mockMvc.perform(post("/api/v1/classes").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "academicYearId", yearId,
                                "name", "Class 9",
                                "section", "A",
                                "capacity", 40,
                                "classTeacherId", teacherId))))
                .andExpect(status().isCreated())
                .andReturn();
        long class1Id = objectMapper.readTree(class1Res.getResponse().getContentAsString()).get("id").asLong();

        MvcResult class2Res = mockMvc.perform(post("/api/v1/classes").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "academicYearId", yearId,
                                "name", "Class 10",
                                "section", "A",
                                "capacity", 40))))
                .andExpect(status().isCreated())
                .andReturn();
        long class2Id = objectMapper.readTree(class2Res.getResponse().getContentAsString()).get("id").asLong();

        MvcResult subRes = mockMvc.perform(post("/api/v1/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MAT101\",\"name\":\"Mathematics\",\"fullMarks\":100,\"passMarks\":33}"))
                .andExpect(status().isCreated())
                .andReturn();
        long subjectId = objectMapper.readTree(subRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/v1/classes/" + class2Id + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", subjectId,
                                "teacherId", teacherId))))
                .andExpect(status().isOk());

        // GET /teachers/{id}/teaching-load: ADMIN and TEACHER allowed, STUDENT 403, missing 404
        for (String role : new String[] {"ADMIN", "TEACHER"}) {
            mockMvc.perform(get(URL + "/" + teacherId + "/teaching-load").with(as(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.classTeacherOf.length()").value(1))
                    .andExpect(jsonPath("$.classTeacherOf[0].classId").value(class1Id))
                    .andExpect(jsonPath("$.classTeacherOf[0].className").value("Class 9 - A"))
                    .andExpect(jsonPath("$.classTeacherOf[0].academicYear").value("2026"))
                    .andExpect(jsonPath("$.subjects.length()").value(1))
                    .andExpect(jsonPath("$.subjects[0].classId").value(class2Id))
                    .andExpect(jsonPath("$.subjects[0].className").value("Class 10 - A"))
                    .andExpect(jsonPath("$.subjects[0].academicYear").value("2026"))
                    .andExpect(jsonPath("$.subjects[0].subjectId").value(subjectId))
                    .andExpect(jsonPath("$.subjects[0].subjectCode").value("MAT101"))
                    .andExpect(jsonPath("$.subjects[0].subjectName").value("Mathematics"));
        }
        mockMvc.perform(get(URL + "/" + teacherId + "/teaching-load").with(as("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/999999/teaching-load").with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Teacher 999999 was not found"));

        // GET /teachers/me/teaching-load: TEACHER with profile allowed, unlinked TEACHER 403, ADMIN/STUDENT 403, anon 401
        AppUser rafiqUser = appUserRepository.findByUsernameIgnoreCase("t-4001").orElseThrow();
        rafiqUser.setMustChangePassword(false);
        appUserRepository.saveAndFlush(rafiqUser);
        RequestPostProcessor rafiq = jwt().jwt(t -> t.subject("t-4001"))
                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"));
        mockMvc.perform(get(URL + "/me/teaching-load").with(rafiq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classTeacherOf[0].classId").value(class1Id))
                .andExpect(jsonPath("$.subjects[0].subjectCode").value("MAT101"));

        mockMvc.perform(get(URL + "/me/teaching-load").with(as("TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No teacher profile is linked to this account"));
        mockMvc.perform(get(URL + "/me/teaching-load").with(as("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/me/teaching-load").with(as("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/me/teaching-load"))
                .andExpect(status().isUnauthorized());
    }
}
