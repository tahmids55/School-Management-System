package com.school.auth;

import com.school.CommittedIntegrationTestBase;
import com.school.audit.AuditLogRepository;
import com.school.user.AdminBootstrap;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthCommittedIntegrationTest extends CommittedIntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
