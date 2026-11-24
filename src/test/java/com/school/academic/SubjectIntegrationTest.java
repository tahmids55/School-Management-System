package com.school.academic;

import com.school.IntegrationTestBase;
import com.school.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static com.school.academic.AcademicYearIntegrationTest.as;
import static com.school.academic.AcademicYearIntegrationTest.seedTestUsers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubjectIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/v1/subjects";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @BeforeEach
    void setUpUsers() {
        seedTestUsers(appUserRepository);
    }

    private String body(String code, String name, Object fullMarks, Object passMarks) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "code", code, "name", name, "fullMarks", fullMarks, "passMarks", passMarks));
    }

    private ResultActions create(String code, String name, int fullMarks, int passMarks) throws Exception {
        return mockMvc.perform(post(URL).with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(code, name, fullMarks, passMarks)));
    }

    private long createId(String code, String name, int fullMarks, int passMarks) throws Exception {
        MvcResult result = create(code, name, fullMarks, passMarks).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void fullCrud_asAdmin() throws Exception {
        create("  phy101 ", " Physics ", 100, 33)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").value("PHY101"))
                .andExpect(jsonPath("$.name").value("Physics"))
                .andExpect(jsonPath("$.fullMarks").value(100))
                .andExpect(jsonPath("$.passMarks").value(33));
        long id = objectMapper.readTree(mockMvc.perform(get(URL).with(as("ADMIN")))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();

        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PHY101"));

        mockMvc.perform(put(URL + "/" + id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("phy102", "Physics II", 75, 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PHY102"))
                .andExpect(jsonPath("$.fullMarks").value(75));

        mockMvc.perform(delete(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Subject " + id + " was not found"));
    }

    @Test
    void list_isOrderedByCode() throws Exception {
        createId("PHY101", "Physics", 100, 33);
        createId("BAN101", "Bangla", 100, 33);
        createId("MAT101", "Mathematics", 100, 33);

        mockMvc.perform(get(URL).with(as("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("BAN101"))
                .andExpect(jsonPath("$[1].code").value("MAT101"))
                .andExpect(jsonPath("$[2].code").value("PHY101"));
    }

    @Test
    void duplicateCode_ignoringCaseAndSpaces_is409() throws Exception {
        createId("PHY101", "Physics", 100, 33);
        long other = createId("CHE101", "Chemistry", 100, 33);

        create(" phy101 ", "Physics again", 100, 33)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Subject code PHY101 already exists"));
        mockMvc.perform(put(URL + "/" + other).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Phy101", "Chemistry", 100, 33)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Subject code PHY101 already exists"));
    }

    @Test
    void markBoundaries() throws Exception {
        create("A1", "Pass equals full", 50, 50).andExpect(status().isCreated());
        create("A2", "Pass zero", 1, 0).andExpect(status().isCreated());
        create("A3", "Max full", 1000, 1000).andExpect(status().isCreated());

        create("B1", "Pass above full", 50, 51)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Pass marks cannot be greater than full marks"));
        create("B2", "Full zero", 0, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullMarks").isNotEmpty());
        create("B3", "Full too big", 1001, 10)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullMarks").isNotEmpty());
        create("B4", "Pass negative", 100, -1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.passMarks").isNotEmpty());
    }

    @Test
    void invalidInput_is400_neverServerError() throws Exception {
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" \",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").isNotEmpty())
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.fullMarks").isNotEmpty())
                .andExpect(jsonPath("$.errors.passMarks").isNotEmpty());
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("X1", "Text marks", "many", 10)))
                .andExpect(status().isBadRequest());
        create("ABCDEFGHIJKLMNOPQRSTU", "Too long code", 100, 33)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").isNotEmpty());
        mockMvc.perform(put(URL + "/999999").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("X2", "Missing", 100, 33)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Subject 999999 was not found"));
    }

    @Test
    void reads_needLogin_andAnyRoleIsAllowed() throws Exception {
        long id = createId("PHY101", "Physics", 100, 33);

        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/" + id)).andExpect(status().isUnauthorized());
        for (String role : new String[] {"ADMIN", "TEACHER", "STUDENT"}) {
            mockMvc.perform(get(URL).with(as(role))).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/" + id).with(as(role))).andExpect(status().isOk());
        }
    }

    @Test
    void writes_adminAllowed_teacherAndStudentDenied() throws Exception {
        long id = createId("PHY101", "Physics", 100, 33);

        for (String role : new String[] {"TEACHER", "STUDENT"}) {
            mockMvc.perform(post(URL).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("CHE101", "Chemistry", 100, 33)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mockMvc.perform(put(URL + "/" + id).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("PHY101", "Hacked", 100, 33)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete(URL + "/" + id).with(as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(delete(URL + "/" + id)).andExpect(status().isUnauthorized());

        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(jsonPath("$.name").value("Physics"));
        mockMvc.perform(put(URL + "/" + id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PHY101", "Physics 1st paper", 100, 33)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/" + id).with(as("ADMIN"))).andExpect(status().isNoContent());
    }

    /** Last statement in its test: the failed foreign key aborts the shared test transaction. */
    @Test
    void delete_subjectUsedByAClass_is409() throws Exception {
        long subjectId = createId("PHY101", "Physics", 100, 33);
        Long yearId = jdbcTemplate.queryForObject("INSERT INTO academic_years (name, start_date, end_date, created_at, updated_at)"
                + " VALUES ('2026', '2026-01-01', '2026-12-31', now(), now()) RETURNING id", Long.class);
        Long classId = jdbcTemplate.queryForObject("INSERT INTO school_classes (academic_year_id, name, section, capacity, created_at, updated_at)"
                + " VALUES (?, 'Class 9', 'A', 40, now(), now()) RETURNING id", Long.class, yearId);
        jdbcTemplate.update("INSERT INTO class_subjects (school_class_id, subject_id, created_at, updated_at)"
                + " VALUES (?, ?, now(), now())", classId, subjectId);

        mockMvc.perform(delete(URL + "/" + subjectId).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
