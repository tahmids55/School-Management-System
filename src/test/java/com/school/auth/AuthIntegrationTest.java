package com.school.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.school.IntegrationTestBase;
import com.school.audit.AuditLogRepository;
import com.school.security.SecurityProperties;
import com.school.security.TeachingScope;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AuthIntegrationTest.RbacProbeConfig.class)
class AuthIntegrationTest extends IntegrationTestBase {

    private static final String EXPECTED_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; "
                    + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private TeachingScope teachingScope;

    @Autowired
    private Clock clock;

    private AppUser admin;
    private AppUser teacher;
    private AppUser student;
    private AppUser disabledUser;

    @BeforeEach
    void setUpUsers() {
        admin = appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN
        ));
        teacher = appUserRepository.save(new AppUser(
                "t-1001", "ayesha@school.edu", passwordEncoder.encode("TeacherPass#1"), "Ayesha Rahman", Role.TEACHER
        ));
        student = appUserRepository.save(new AppUser(
                "adm-2026-001", "student@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam", Role.STUDENT
        ));
        disabledUser = new AppUser(
                "disabled1", "disabled@school.edu", passwordEncoder.encode("DisabledPass#1"), "Disabled User", Role.TEACHER
        );
        disabledUser.setEnabled(false);
        appUserRepository.save(disabledUser);
    }

    @Test
    void storedPassword_isBcryptHashAndNotPlaintext() {
        AppUser persisted = appUserRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        assertThat(persisted.getPasswordHash()).startsWith("$2a$12$");
        assertThat(persisted.getPasswordHash()).isNotEqualTo("AdminPass#1");
        assertThat(passwordEncoder.matches("AdminPass#1", persisted.getPasswordHash())).isTrue();
    }

    @Test
    void login_byUsernameAndEmail_succeedsAndReturnsJwtAndAudits() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "ADMIN", "password", "AdminPass#1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(admin.getId()))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.fullName").value("Nasima Akter"))
                .andExpect(jsonPath("$.user.email").value("admin@school.edu"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andExpect(jsonPath("$.user.studentId").doesNotExist())
                .andExpect(jsonPath("$.user.teacherId").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "AYESHA@SCHOOL.EDU", "password", "TeacherPass#1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("t-1001"))
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andExpect(jsonPath("$.user.studentId").doesNotExist())
                .andExpect(jsonPath("$.user.teacherId").doesNotExist());

        assertThat(auditLogRepository.findAll())
                .filteredOn(entry -> "LOGIN_SUCCESS".equals(entry.getAction()))
                .hasSize(2);
    }

    @Test
    void login_wrongPassword_andUnknownUser_return401WithSameMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "WrongPass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "nobody", "password", "WrongPass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(auditLogRepository.findAll())
                .filteredOn(entry -> "LOGIN_FAILED".equals(entry.getAction()))
                .hasSize(2);
    }

    @Test
    void login_disabledUser_revealsDisabledOnlyAfterCorrectPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "disabled1", "password", "WrongPass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "disabled1", "password", "DisabledPass#1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This account is disabled. Contact the administrator."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void login_invalidPayload_returns400ProblemJsonWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Some fields are invalid"))
                .andExpect(jsonPath("$.errors.username").isNotEmpty())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    void me_withoutTokenOrTamperedOrExpired_returns401ProblemJson() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Authentication required. Please log in again."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Authentication required. Please log in again."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        String expiredToken = signExpiredJwt(admin);
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void me_withValidToken_returnsCurrentUser_andRejectsWhenAccountDisabled() throws Exception {
        String token = loginAndGetToken("adm-2026-001", "StudentPass#1");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student.getId()))
                .andExpect(jsonPath("$.username").value("adm-2026-001"))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        student.setEnabled(false);
        appUserRepository.save(student);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This account is disabled. Contact the administrator."));
    }

    @Test
    void roles_enforceAdminActuatorAndPreAuthorizeRules_with403ProblemJson() throws Exception {
        String adminToken = loginAndGetToken("admin", "AdminPass#1");
        String teacherToken = loginAndGetToken("t-1001", "TeacherPass#1");
        String studentToken = loginAndGetToken("adm-2026-001", "StudentPass#1");

        // /actuator/metrics: ADMIN allowed; TEACHER & STUDENT 403 problem+json; anonymous 401
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action"));

        // @PreAuthorize probe endpoints via real JWT tokens
        mockMvc.perform(get("/api/v1/test-rbac/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/test-rbac/admin")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action"));

        mockMvc.perform(get("/api/v1/test-rbac/teacher")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/test-rbac/teacher")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/v1/test-rbac/student")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/test-rbac/student")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void registerEndpoint_doesNotWorkForAnyone() throws Exception {
        String body = "{\"username\":\"hacker\",\"email\":\"h@school.edu\",\"password\":\"Secret123!\"}";
        long usersBefore = appUserRepository.count();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        for (String token : List.of(
                loginAndGetToken("admin", "AdminPass#1"),
                loginAndGetToken("t-1001", "TeacherPass#1"),
                loginAndGetToken("adm-2026-001", "StudentPass#1")
        )) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is(not(200)))
                    .andExpect(status().is(not(201)))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        assertThat(appUserRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    void securityHeaders_includeExactCspAndReferrerPolicy_andDefaultTeachingScopeIsEmpty() throws Exception {
        assertThat(teachingScope.accessibleClassIds(teacher.getUsername())).isEmpty();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", EXPECTED_CSP))
                .andExpect(header().string("Referrer-Policy", "same-origin"));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String signExpiredJwt(AppUser user) throws Exception {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(securityProperties.getJwt().getIssuer())
                .issueTime(Date.from(now.minusSeconds(1800)))
                .expirationTime(Date.from(now.minusSeconds(60)))
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("name", user.getFullName())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(securityProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }

    @TestConfiguration
    static class RbacProbeConfig {

        @RestController
        @RequestMapping("/api/v1/test-rbac")
        static class RbacProbeController {

            @PreAuthorize("hasRole('ADMIN')")
            @GetMapping("/admin")
            String adminOnly() {
                return "admin";
            }

            @PreAuthorize("hasRole('TEACHER')")
            @GetMapping("/teacher")
            String teacherOnly() {
                return "teacher";
            }

            @PreAuthorize("hasRole('STUDENT')")
            @GetMapping("/student")
            String studentOnly() {
                return "student";
            }
        }
    }
}
