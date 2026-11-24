package com.school.academic;

import com.school.IntegrationTestBase;
import com.school.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AcademicYearIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/v1/academic-years";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(role.toLowerCase())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String body(String name, String start, String end) throws Exception {
        return objectMapper.writeValueAsString(Map.of("name", name, "startDate", start, "endDate", end));
    }

    private ResultActions create(String name, String start, String end) throws Exception {
        return mockMvc.perform(post(URL).with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name, start, end)));
    }

    private long createId(String name, String start, String end) throws Exception {
        MvcResult result = create(name, start, end).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Integer currentCountInDatabase() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM academic_years WHERE current", Integer.class);
    }

    @Test
    void fullCrud_asAdmin() throws Exception {
        create(" 2026 ", "2026-01-01", "2026-12-31")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("2026"))
                .andExpect(jsonPath("$.startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.endDate").value("2026-12-31"))
                .andExpect(jsonPath("$.current").value(true));
        long id = objectMapper.readTree(mockMvc.perform(get(URL).with(as("ADMIN")))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();

        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2026"));

        mockMvc.perform(put(URL + "/" + id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-27", "2026-01-15", "2027-01-14")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2026-27"))
                .andExpect(jsonPath("$.startDate").value("2026-01-15"))
                .andExpect(jsonPath("$.current").value(true));

        long old = createId("2025", "2025-01-01", "2025-12-31");
        mockMvc.perform(delete(URL + "/" + old).with(as("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(URL + "/" + old).with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Academic year " + old + " was not found"));

        assertThat(auditLogRepository.findAll())
                .filteredOn(entry -> "ACADEMIC_YEAR_CREATED".equals(entry.getAction()))
                .hasSize(2);
    }

    @Test
    void list_isNewestFirst_andOnlyFirstYearIsCurrent() throws Exception {
        createId("2024", "2024-01-01", "2024-12-31");
        createId("2026", "2026-01-01", "2026-12-31");
        createId("2025", "2025-01-01", "2025-12-31");

        mockMvc.perform(get(URL).with(as("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("2026"))
                .andExpect(jsonPath("$[1].name").value("2025"))
                .andExpect(jsonPath("$[2].name").value("2024"))
                .andExpect(jsonPath("$[2].current").value(true))
                .andExpect(jsonPath("$[0].current").value(false));
        assertThat(currentCountInDatabase()).isEqualTo(1);
    }

    @Test
    void activate_movesCurrentFlag_soOnlyOneYearIsCurrent() throws Exception {
        long first = createId("2025", "2025-01-01", "2025-12-31");
        long second = createId("2026", "2026-01-01", "2026-12-31");

        mockMvc.perform(post(URL + "/" + second + "/activate").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second))
                .andExpect(jsonPath("$.current").value(true));

        assertThat(currentCountInDatabase()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current FROM academic_years WHERE id = ?", Boolean.class, first)).isFalse();
        mockMvc.perform(get(URL + "/current").with(as("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2026"));

        // Activating the current year again is harmless.
        mockMvc.perform(post(URL + "/" + second + "/activate").with(as("ADMIN")))
                .andExpect(status().isOk());
        assertThat(currentCountInDatabase()).isEqualTo(1);
        assertThat(auditLogRepository.findAll())
                .filteredOn(entry -> "ACADEMIC_YEAR_ACTIVATED".equals(entry.getAction()))
                .hasSize(1);
    }

    @Test
    void current_whenNoYearExists_is404() throws Exception {
        mockMvc.perform(get(URL + "/current").with(as("STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No current academic year is set"));
    }

    @Test
    void duplicateName_ignoringCase_is409() throws Exception {
        createId("Session-A", "2026-01-01", "2026-12-31");

        create("  session-a ", "2027-01-01", "2027-12-31")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Academic year session-a already exists"));

        long other = createId("Session-B", "2027-01-01", "2027-12-31");
        mockMvc.perform(put(URL + "/" + other).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SESSION-A", "2027-01-01", "2027-12-31")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Academic year SESSION-A already exists"));
    }

    @Test
    void dateBoundaries_endMustBeStrictlyAfterStart() throws Exception {
        create("Same", "2026-01-01", "2026-01-01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("End date must be after start date"));
        create("Before", "2026-01-02", "2026-01-01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("End date must be after start date"));
        create("OneDay", "2026-01-01", "2026-01-02")
                .andExpect(status().isCreated());
    }

    @Test
    void invalidInput_is400_neverServerError() throws Exception {
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"startDate\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.startDate").isNotEmpty())
                .andExpect(jsonPath("$.errors.endDate").isNotEmpty());
        create("123456789012345678901", "2026-01-01", "2026-12-31")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026\",\"startDate\":\"not-a-date\",\"endDate\":\"2026-12-31\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/abc").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL + "/999999/activate").with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Academic year 999999 was not found"));
    }

    @Test
    void delete_currentYear_is409() throws Exception {
        long id = createId("2026", "2026-01-01", "2026-12-31");

        mockMvc.perform(delete(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The current academic year cannot be deleted"));
    }

    @Test
    void delete_yearWithClasses_is409() throws Exception {
        createId("2026", "2026-01-01", "2026-12-31");
        long old = createId("2025", "2025-01-01", "2025-12-31");
        jdbcTemplate.update("INSERT INTO school_classes (academic_year_id, name, section, capacity, created_at, updated_at)"
                + " VALUES (?, 'Class 9', 'A', 40, now(), now())", old);

        mockMvc.perform(delete(URL + "/" + old).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This academic year has classes or exams and cannot be deleted"));
    }

    @Test
    void reads_needLogin_andAnyRoleIsAllowed() throws Exception {
        long id = createId("2026", "2026-01-01", "2026-12-31");

        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/" + id)).andExpect(status().isUnauthorized());
        for (String role : new String[] {"ADMIN", "TEACHER", "STUDENT"}) {
            mockMvc.perform(get(URL).with(as(role))).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/" + id).with(as(role))).andExpect(status().isOk());
        }
    }

    @Test
    void writes_adminAllowed_teacherAndStudentDenied() throws Exception {
        long id = createId("2026", "2026-01-01", "2026-12-31");
        long other = createId("2027", "2027-01-01", "2027-12-31");

        for (String role : new String[] {"TEACHER", "STUDENT"}) {
            mockMvc.perform(post(URL).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("2030", "2030-01-01", "2030-12-31")))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mockMvc.perform(put(URL + "/" + id).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("2030", "2030-01-01", "2030-12-31")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(URL + "/" + other + "/activate").with(as(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete(URL + "/" + other).with(as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2030", "2030-01-01", "2030-12-31")))
                .andExpect(status().isUnauthorized());

        // Nothing changed through the denied calls; the admin can do each one.
        mockMvc.perform(post(URL + "/" + other + "/activate").with(as("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(put(URL + "/" + id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026", "2026-01-01", "2026-12-30")))
                .andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/" + id).with(as("ADMIN"))).andExpect(status().isNoContent());
    }
}
