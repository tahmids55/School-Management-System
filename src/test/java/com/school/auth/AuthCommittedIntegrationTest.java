package com.school.auth;

import com.school.CommittedIntegrationTestBase;
import com.school.audit.AuditLogRepository;
import com.school.user.AdminBootstrap;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthCommittedIntegrationTest extends CommittedIntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void loginFailure_commitsLoginFailedAuditRowEvenWhen401Thrown() throws Exception {
        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "WrongPass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "missing", "password", "WrongPass"))))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.findAll())
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.getAction()).isEqualTo("LOGIN_FAILED"));
    }

    @Test
    void parallelLoginAttempts_40Threads_locksAccountWithout409Or500() throws Exception {
        appUserRepository.save(new AppUser(
                "victim", "victim@school.edu", passwordEncoder.encode("Correct#123"), "Victim User", Role.TEACHER
        ));
        String wrongBody = objectMapper.writeValueAsString(Map.of("username", "victim", "password", "WrongPass"));

        int threads = 40;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongBody))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(executor.submit(task));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(statuses)
                .hasSize(threads)
                .allMatch(status -> status == 401);

        AppUser updated = appUserRepository.findByUsernameIgnoreCase("victim").orElseThrow();
        assertThat(updated.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(updated.getLockedUntil()).isNotNull().isAfter(clock.instant());
    }

    @Test
    void parallelTwoTabRefresh_bothSucceed_andLateReuseCommitsTokenDeletion() throws Exception {
        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN
        ));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "AdminPass#1"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie initialCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(initialCookie).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/auth/refresh").cookie(initialCookie)).andReturn();
            }));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        MvcResult res1 = futures.get(0).get(10, TimeUnit.SECONDS);
        MvcResult res2 = futures.get(1).get(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(res1.getResponse().getStatus()).isEqualTo(200);
        assertThat(res2.getResponse().getStatus()).isEqualTo(200);

        Cookie next1 = res1.getResponse().getCookie("refresh_token");
        Cookie next2 = res2.getResponse().getCookie("refresh_token");
        assertThat(next1).isNotNull();
        assertThat(next2).isNotNull();
        assertThat(next1.getValue()).isNotEqualTo(next2.getValue());

        // Move revoked_at outside the 10s grace window and verify 401 still commits full deletion
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?",
                Timestamp.from(clock.instant().minusSeconds(11)),
                refreshTokenService.hash(initialCookie.getValue())
        );

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(initialCookie))
                .andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void adminBootstrap_createsInitialAdminInPostgreSQLAndIsIdempotent() {
        AdminBootstrap bootstrap = new AdminBootstrap(appUserRepository, passwordEncoder, "BootPass#2026");

        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());

        assertThat(appUserRepository.findAll())
                .singleElement()
                .satisfies(admin -> {
                    assertThat(admin.getUsername()).isEqualTo("admin");
                    assertThat(admin.getEmail()).isEqualTo("admin@school.edu");
                    assertThat(admin.getFullName()).isEqualTo("Nasima Akter");
                    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
                    assertThat(admin.getPasswordHash()).startsWith("$2");
                    assertThat(admin.getPasswordHash()).isNotEqualTo("BootPass#2026");
                    assertThat(passwordEncoder.matches("BootPass#2026", admin.getPasswordHash())).isTrue();
                });
    }
}
