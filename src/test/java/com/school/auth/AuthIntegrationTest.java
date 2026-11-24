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
import com.school.user.UserDtos.TemporaryPasswordResponse;
import com.school.user.UserService;
import com.school.user.UserService.NewAccount;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private TeachingScope teachingScope;

    @Autowired
    private UserService userService;

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

    @Test
    void login_setsHttpOnlyStrictRefreshCookie_andStoresOnlySha256Hash() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "AdminPass#1"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refresh_token="),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/v1/auth"),
                        containsString("Max-Age=604800")
                )))
                .andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isNotBlank();

        List<RefreshToken> stored = refreshTokenRepository.findAll();
        assertThat(stored).singleElement().satisfies(rt -> {
            assertThat(rt.getTokenHash()).isEqualTo(refreshTokenService.hash(refreshCookie.getValue()));
            assertThat(rt.getTokenHash()).isNotEqualTo(refreshCookie.getValue());
            assertThat(rt.getRevokedAt()).isNull();
        });
    }

    @Test
    void refresh_rotatesCookie_allowsTwoTabGraceWithin10s_andSucceedsWithExpiredBearerHeader() throws Exception {
        Cookie initialCookie = loginAndGetRefreshCookie("t-1001", "TeacherPass#1");
        String expiredAccessToken = signExpiredJwt(teacher);

        MvcResult tab1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer " + expiredAccessToken)
                        .cookie(initialCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("t-1001"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refresh_token="),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/v1/auth")
                )))
                .andReturn();

        Cookie tab1Cookie = tab1.getResponse().getCookie("refresh_token");
        assertThat(tab1Cookie).isNotNull();
        assertThat(tab1Cookie.getValue()).isNotEqualTo(initialCookie.getValue());

        // Second parallel tab using the same initial cookie inside the real 10s grace period succeeds
        MvcResult tab2 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(initialCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie tab2Cookie = tab2.getResponse().getCookie("refresh_token");
        assertThat(tab2Cookie).isNotNull();
        assertThat(tab2Cookie.getValue()).isNotEqualTo(tab1Cookie.getValue());
    }

    @Test
    void refresh_lateReuseOutsideGraceWindow_returns401AndDeletesAllUserSessions() throws Exception {
        Cookie initialCookie = loginAndGetRefreshCookie("t-1001", "TeacherPass#1");
        Cookie rotatedCookie = refreshAndGetCookie(initialCookie);
        Cookie secondSessionCookie = loginAndGetRefreshCookie("t-1001", "TeacherPass#1");

        // Age revoked_at beyond the 10s grace window while keeping app.security.refresh-reuse-grace=10s
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(11)),
                refreshTokenService.hash(initialCookie.getValue())
        );
        entityManager.clear();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(initialCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."));

        // All sessions of that user are now gone
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(secondSessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withoutCookie_returns401ProblemJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."));
    }

    @Test
    void logout_clearsCookieAndDeletesPresentedToken_soImmediateReplayReturns401() throws Exception {
        Cookie cookie = loginAndGetRefreshCookie("adm-2026-001", "StudentPass#1");

        mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refresh_token="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/v1/auth")
                )));

        // Immediate replay inside 10s is 401 because logout DELETED the row
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."));
    }

    @Test
    void logout_withRotatedToken_deletesAllTokensOfUser_andWithoutCookie_returns204() throws Exception {
        Cookie initialCookie = loginAndGetRefreshCookie("adm-2026-001", "StudentPass#1");
        Cookie activeCookie = refreshAndGetCookie(initialCookie);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(initialCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(activeCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void revokeAllForUser_invalidatesAllCookiesImmediately() throws Exception {
        Cookie cookie1 = loginAndGetRefreshCookie("t-1001", "TeacherPass#1");
        Cookie cookie2 = loginAndGetRefreshCookie("t-1001", "TeacherPass#1");

        refreshTokenService.revokeAllForUser(teacher);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie1))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockoutBoundary_fourWrongAllowsFifthRight_fiveWrongLocks15Min_andUnlocksAfter15Min() throws Exception {
        // 4 wrong passwords do not lock yet
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("username", "t-1001", "password", "Wrong"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        }

        // Right password on 5th attempt succeeds and resets counter
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "t-1001", "password", "TeacherPass#1"))))
                .andExpect(status().isOk());

        AppUser afterReset = appUserRepository.findByUsernameIgnoreCase("t-1001").orElseThrow();
        assertThat(afterReset.getFailedLoginAttempts()).isZero();
        assertThat(afterReset.getLockedUntil()).isNull();

        // 5 wrong passwords lock for 15 minutes
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("username", "t-1001", "password", "Wrong"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        }

        AppUser locked = appUserRepository.findByUsernameIgnoreCase("t-1001").orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(clock.instant().plus(Duration.ofMinutes(14)));

        // Even the right password is rejected with the exact same generic message while locked
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "t-1001", "password", "TeacherPass#1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getActor()).isEqualTo("t-1001");
                    assertThat(entry.getAction()).isEqualTo("LOGIN_FAILED");
                    assertThat(entry.getDetails()).containsIgnoringCase("locked");
                });

        // Simulate 15 minutes having elapsed
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE users SET locked_until = ? WHERE id = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)),
                locked.getId()
        );
        entityManager.clear();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "t-1001", "password", "TeacherPass#1"))))
                .andExpect(status().isOk());

        AppUser unlocked = appUserRepository.findByUsernameIgnoreCase("t-1001").orElseThrow();
        assertThat(unlocked.getFailedLoginAttempts()).isZero();
        assertThat(unlocked.getLockedUntil()).isNull();
    }

    @Test
    void changePassword_weakPassword_returns400WithFieldError() throws Exception {
        String token = loginAndGetToken("t-1001", "TeacherPass#1");

        for (String weak : List.of("Abc1234", "onlyletters", "12345678", "A1" + "b".repeat(99))) {
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "TeacherPass#1",
                                    "newPassword", weak
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Some fields are invalid"))
                    .andExpect(jsonPath("$.errors.newPassword")
                            .value("must be 8-100 characters and contain a letter and a digit"));
        }
    }

    @Test
    void changePassword_boundariesAndValidationRules_enforcedCorrectly() throws Exception {
        // Unauthenticated is 401
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"TeacherPass#1\",\"newPassword\":\"Valid123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String token = loginAndGetToken("t-1001", "TeacherPass#1");

        // Wrong current password -> 422
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "WrongPass1",
                                "newPassword", "ValidPass1"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));

        // Same as current -> 422
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "TeacherPass#1",
                                "newPassword", "TeacherPass#1"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("New password must be different from the current password"));

        // Exact 8-char boundary succeeds (204)
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "TeacherPass#1",
                                "newPassword", "Abcdef12"
                        ))))
                .andExpect(status().isNoContent());

        // Exact 72-char BCrypt limit boundary succeeds (204)
        String maxValid = "A1" + "b".repeat(70);
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Abcdef12",
                                "newPassword", maxValid
                        ))))
                .andExpect(status().isNoContent());

        // ADMIN and STUDENT roles can also change their own passwords
        String adminToken = loginAndGetToken("admin", "AdminPass#1");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "AdminPass#1",
                                "newPassword", "AdminPass#2"
                        ))))
                .andExpect(status().isNoContent());

        String studentToken = loginAndGetToken("adm-2026-001", "StudentPass#1");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "StudentPass#1",
                                "newPassword", "StudentPass#2"
                        ))))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_clearsMustChangeFlagRevokesAllCookiesAndAudits() throws Exception {
        NewAccount created = userService.createAccount(
                "T-2001",
                "NEWTEACHER@SCHOOL.EDU",
                "New Teacher",
                Role.TEACHER
        );
        assertThat(created.user().getUsername()).isEqualTo("t-2001");
        assertThat(created.user().getEmail()).isEqualTo("newteacher@school.edu");
        assertThat(created.user().isMustChangePassword()).isTrue();
        assertThat(created.temporaryPassword()).matches("^(?=.*[A-Za-z])(?=.*\\d).{12}$");

        Cookie session1Cookie = loginAndGetRefreshCookie("t-2001", created.temporaryPassword());
        MvcResult login2 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "t-2001",
                                "password", created.temporaryPassword()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        String accessToken = objectMapper.readTree(login2.getResponse().getContentAsString()).get("accessToken").asText();
        Cookie session2Cookie = login2.getResponse().getCookie("refresh_token");
        assertThat(session2Cookie).isNotNull();

        // While mustChangePassword is true: /auth/me works (200), normal endpoints return 403
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(get("/api/v1/test-rbac/teacher")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Change your temporary password first"));

        // Change password -> 204 and clears refresh cookie
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", created.temporaryPassword(),
                                "newPassword", "MyNewPass#99"
                        ))))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refresh_token="),
                        containsString("Max-Age=0")
                )));

        // Both old refresh cookies fail immediately
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session1Cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session2Cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."));

        // Normal endpoint works now
        mockMvc.perform(get("/api/v1/test-rbac/teacher")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> assertThat(entry.getAction()).isEqualTo("ACCOUNT_CREATED"))
                .anySatisfy(entry -> assertThat(entry.getAction()).isEqualTo("PASSWORD_CHANGED"));
    }

    @Test
    void resetPassword_revokesOldCookiesImmediatelyAndSetsMustChangePassword() throws Exception {
        Cookie cookieBeforeReset = loginAndGetRefreshCookie("adm-2026-001", "StudentPass#1");

        TemporaryPasswordResponse reset = userService.resetPassword(student.getId());
        assertThat(reset.username()).isEqualTo("adm-2026-001");
        assertThat(reset.temporaryPassword()).hasSize(12);

        // Old cookie fails straight after reset
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookieBeforeReset))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."));

        // Login with temporary password triggers mustChangePassword=true and 403 on normal endpoint
        String tempToken = loginAndGetToken("adm-2026-001", reset.temporaryPassword());
        mockMvc.perform(get("/api/v1/test-rbac/student")
                        .header("Authorization", "Bearer " + tempToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Change your temporary password first"));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> assertThat(entry.getAction()).isEqualTo("PASSWORD_RESET"));
    }

    @Test
    void disableAndDeletedUser_existingAccessTokenGets401OnNormalEndpoints() throws Exception {
        Cookie cookie = loginAndGetRefreshCookie("adm-2026-001", "StudentPass#1");
        String token = loginAndGetToken("adm-2026-001", "StudentPass#1");

        mockMvc.perform(get("/api/v1/test-rbac/student")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        userService.disable(student);

        mockMvc.perform(get("/api/v1/test-rbac/student")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This account is disabled. Contact the administrator."));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());

        // Also test deleted user whose JWT is still syntactically valid
        String teacherToken = loginAndGetToken("t-1001", "TeacherPass#1");
        AppUser freshTeacher = appUserRepository.findById(teacher.getId()).orElseThrow();
        refreshTokenService.revokeAllForUser(freshTeacher);
        appUserRepository.delete(freshTeacher);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/test-rbac/teacher")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."));
    }

    private Cookie loginAndGetRefreshCookie(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private Cookie refreshAndGetCookie(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        Cookie next = result.getResponse().getCookie("refresh_token");
        assertThat(next).isNotNull();
        return next;
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
