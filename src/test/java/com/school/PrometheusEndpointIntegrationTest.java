package com.school;

import com.school.security.JwtService;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureObservability
@TestPropertySource(properties = {
        "METRICS_USERNAME=prometheus",
        "METRICS_PASSWORD=scrape-secret"
})
class PrometheusEndpointIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void prometheusEndpointRejectsAnonymousWrongCredentialsAndJwtOnly() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Basic")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "wrong-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("wrong-user", "scrape-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String adminToken = createToken("prom_admin", Role.ADMIN);
        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusEndpointReturnsMetricsAndHistogramWithValidBasicAuth() throws Exception {
        // Trigger at least one HTTP request so http_server_requests_seconds_bucket is populated
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "scrape-secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("http_server_requests_seconds_bucket"),
                        containsString("jvm_memory_used_bytes"),
                        containsString("process_cpu_usage"),
                        containsString("hikaricp_connections_active")
                )));
    }

    @Test
    void otherActuatorSecurityRulesRemainUnchanged() throws Exception {
        // Basic metrics credentials do not grant access to /actuator/metrics
        mockMvc.perform(get("/actuator/metrics").with(httpBasic("prometheus", "scrape-secret")))
                .andExpect(status().isUnauthorized());

        String teacherToken = createToken("prom_teacher", Role.TEACHER);
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        String adminToken = createToken("prom_admin_2", Role.ADMIN);
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private String createToken(String username, Role role) {
        AppUser user = appUserRepository.save(new AppUser(
                username,
                username + "@school.edu",
                "Test User",
                passwordEncoder.encode("Pass1234"),
                role
        ));
        return jwtService.generateAccessToken(user);
    }
}
