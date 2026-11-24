package com.school.academic;

import com.school.IntegrationTestBase;
import com.school.audit.AuditLogRepository;
import com.school.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static com.school.academic.AcademicYearIntegrationTest.as;
import static com.school.academic.AcademicYearIntegrationTest.seedTestUsers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchoolClassIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/v1/classes";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AppUserRepository appUserRepository;

    @BeforeEach
    void setUpUsers() {
        seedTestUsers(appUserRepository);
    }

    /** Creates a year through the API; the first one becomes current. */
    private long year(String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name, "startDate", name + "-01-01", "endDate", name + "-12-31"));
        MvcResult result = mockMvc.perform(post("/api/v1/academic-years").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String body(long yearId, String name, String section, String room, Object capacity) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("academicYearId", yearId);
        map.put("name", name);
        map.put("section", section);
        map.put("room", room);
        map.put("capacity", capacity);
        return objectMapper.writeValueAsString(map);
    }

    private ResultActions create(long yearId, String name, String section) throws Exception {
        return mockMvc.perform(post(URL).with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(yearId, name, section, "R-201", 40)));
    }

    private long createId(long yearId, String name, String section) throws Exception {
        MvcResult result = create(yearId, name, section).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void addStudent(long classId, String admissionNo) {
        jdbcTemplate.update("INSERT INTO students (admission_no, first_name, last_name, gender, date_of_birth,"
                + " guardian_name, guardian_phone, guardian_relation, school_class_id, status, admission_date,"
                + " created_at, updated_at) VALUES (?, 'Test', 'Student', 'FEMALE', '2012-05-01', 'Guardian',"
                + " '01700000000', 'Mother', ?, 'ACTIVE', '2026-01-01', now(), now())", admissionNo, classId);
    }

    @Test
    void fullCrud_asAdmin() throws Exception {
        long yearId = year("2026");

        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, " Class 9 ", " a ", " R-201 ", 40)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.academicYearId").value(yearId))
                .andExpect(jsonPath("$.academicYearName").value("2026"))
                .andExpect(jsonPath("$.name").value("Class 9"))
                .andExpect(jsonPath("$.section").value("A"))
                .andExpect(jsonPath("$.displayName").value("Class 9 - A"))
                .andExpect(jsonPath("$.room").value("R-201"))
                .andExpect(jsonPath("$.capacity").value(40))
                .andExpect(jsonPath("$.studentCount").value(0))
                .andExpect(jsonPath("$.classTeacherId").doesNotExist())
                .andExpect(jsonPath("$.classTeacherName").doesNotExist());
        long id = objectMapper.readTree(mockMvc.perform(get(URL).with(as("ADMIN")))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();

        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Class 9 - A"));

        mockMvc.perform(put(URL + "/" + id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, "Class 9", "b", null, 35)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Class 9 - B"))
                .andExpect(jsonPath("$.room").doesNotExist())
                .andExpect(jsonPath("$.capacity").value(35));

        mockMvc.perform(delete(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Class " + id + " was not found"));

        assertThat(auditLogRepository.findAll()).extracting(entry -> entry.getAction())
                .contains("CLASS_CREATED", "CLASS_DELETED");
    }

    @Test
    void list_defaultsToCurrentYear_isOrderedByNameThenSection_andAcceptsAChosenYear() throws Exception {
        long current = year("2026");
        long next = year("2027");
        createId(current, "Class 7", "B");
        createId(current, "Class 6", "B");
        createId(current, "Class 7", "A");
        createId(current, "Class 6", "A");
        createId(next, "Class 10", "A");

        mockMvc.perform(get(URL).with(as("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].displayName").value("Class 6 - A"))
                .andExpect(jsonPath("$[1].displayName").value("Class 6 - B"))
                .andExpect(jsonPath("$[2].displayName").value("Class 7 - A"))
                .andExpect(jsonPath("$[3].displayName").value("Class 7 - B"));

        mockMvc.perform(get(URL).param("academicYearId", String.valueOf(next)).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].academicYearName").value("2027"));

        mockMvc.perform(get(URL).param("academicYearId", "999999").with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Academic year 999999 was not found"));
        mockMvc.perform(get(URL).param("academicYearId", "abc").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_withoutCurrentYear_isEmpty() throws Exception {
        mockMvc.perform(get(URL).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_usesAFixedNumberOfQueries() throws Exception {
        long yearId = year("2026");
        long first = createId(yearId, "Class 6", "A");
        addStudent(first, "ADM-1");

        long fewClasses = countQueriesOfList(yearId);
        for (String section : new String[] {"B", "C", "D", "E", "F", "G"}) {
            addStudent(createId(yearId, "Class 6", section), "ADM-" + section);
        }
        long manyClasses = countQueriesOfList(yearId);

        // 1 AccountStateFilter user reload + 3 class-list queries (year, classes, student counts)
        assertThat(manyClasses).isEqualTo(fewClasses).isLessThanOrEqualTo(4);
    }

    private long countQueriesOfList(long yearId) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            mockMvc.perform(get(URL).param("academicYearId", String.valueOf(yearId)).with(as("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].studentCount").value(1));
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void duplicateNameAndSection_inSameYear_is409_butAllowedInAnotherYear() throws Exception {
        long current = year("2026");
        long next = year("2027");
        createId(current, "Class 9", "A");
        long other = createId(current, "Class 9", "B");

        create(current, " class 9 ", "a")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("class 9 - A already exists in 2026"));
        mockMvc.perform(put(URL + "/" + other).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(current, "Class 9", "A", null, 40)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Class 9 - A already exists in this academic year"));

        create(next, "Class 9", "A").andExpect(status().isCreated());
    }

    @Test
    void capacityAndDelete_respectEnrolledStudents() throws Exception {
        long yearId = year("2026");
        long id = createId(yearId, "Class 6", "A");
        addStudent(id, "ADM-1");
        addStudent(id, "ADM-2");

        mockMvc.perform(get(URL + "/" + id).with(as("TEACHER")))
                .andExpect(jsonPath("$.studentCount").value(2));
        mockMvc.perform(put(URL + "/" + id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, "Class 6", "A", null, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Capacity cannot be lower than the 2 students already enrolled"));
        mockMvc.perform(put(URL + "/" + id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, "Class 6", "A", null, 2)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Move the students out of Class 6 - A before deleting it"));
    }

    @Test
    void yearWithClasses_cannotBeDeleted() throws Exception {
        year("2026");
        long old = year("2025");
        createId(old, "Class 6", "A");

        mockMvc.perform(delete("/api/v1/academic-years/" + old).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This academic year has classes or exams and cannot be deleted"));
    }

    @Test
    void invalidInput_is400_unknownYearIs404() throws Exception {
        long yearId = year("2026");

        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"section\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.academicYearId").isNotEmpty())
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.section").isNotEmpty())
                .andExpect(jsonPath("$.errors.capacity").isNotEmpty());
        for (Object capacity : new Object[] {0, 201, "many"}) {
            mockMvc.perform(post(URL).with(as("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(yearId, "Class 6", "A", null, capacity)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, "Class 6", "ABCDEFGHIJK", "R-123456789012345678901", 40)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.section").isNotEmpty())
                .andExpect(jsonPath("$.errors.room").isNotEmpty());
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999, "Class 6", "A", null, 40)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Academic year 999999 was not found"));
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, "Class 6", "A", null, 1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, "Class 7", "A", null, 200)))
                .andExpect(status().isCreated());
    }

    @Test
    void reads_adminAndTeacherAllowed_studentDenied() throws Exception {
        long id = createId(year("2026"), "Class 6", "A");

        for (String role : new String[] {"ADMIN", "TEACHER"}) {
            mockMvc.perform(get(URL).with(as(role))).andExpect(status().isOk());
            mockMvc.perform(get(URL + "/" + id).with(as(role))).andExpect(status().isOk());
        }
        mockMvc.perform(get(URL).with(as("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get(URL + "/" + id).with(as("STUDENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void writes_adminAllowed_teacherAndStudentDenied() throws Exception {
        long yearId = year("2026");
        long id = createId(yearId, "Class 6", "A");

        for (String role : new String[] {"TEACHER", "STUDENT"}) {
            mockMvc.perform(post(URL).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(yearId, "Class 8", "A", null, 40)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put(URL + "/" + id).with(as(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(yearId, "Class 6", "Z", null, 40)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete(URL + "/" + id).with(as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(delete(URL + "/" + id)).andExpect(status().isUnauthorized());

        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(jsonPath("$.displayName").value("Class 6 - A"));
        mockMvc.perform(put(URL + "/" + id).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearId, "Class 6", "Z", null, 40)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/" + id).with(as("ADMIN"))).andExpect(status().isNoContent());
    }
}
