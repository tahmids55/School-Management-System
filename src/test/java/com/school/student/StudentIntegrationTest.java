package com.school.student;

import com.fasterxml.jackson.databind.JsonNode;
import com.school.IntegrationTestBase;
import com.school.audit.AuditLogRepository;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.school.academic.AcademicYearIntegrationTest.as;
import static com.school.academic.AcademicYearIntegrationTest.seedTestUsers;
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

class StudentIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/v1/students";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long yearId;

    @BeforeEach
    void setUp() throws Exception {
        seedTestUsers(appUserRepository);
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "2026", "startDate", "2026-01-01", "endDate", "2026-12-31"));
        yearId = idOf(mockMvc.perform(post("/api/v1/academic-years").with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
    }

    // ---------- helpers ----------

    private long idOf(ResultActions result) throws Exception {
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return json.has("student") ? json.get("student").get("id").asLong() : json.get("id").asLong();
    }

    private long createClass(String name, String section, int capacity) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "academicYearId", yearId, "name", name, "section", section, "capacity", capacity));
        return idOf(mockMvc.perform(post("/api/v1/classes").with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
    }

    /** A valid StudentRequest as a map; tests change single fields. */
    private static Map<String, Object> student(String admissionNo, Long classId) {
        Map<String, Object> map = new HashMap<>();
        map.put("admissionNo", admissionNo);
        map.put("firstName", "Rahim");
        map.put("lastName", "Uddin");
        map.put("gender", "MALE");
        map.put("dateOfBirth", "2014-03-09");
        map.put("guardianName", "Abdul Uddin");
        map.put("guardianPhone", "01711111111");
        map.put("guardianRelation", "Father");
        map.put("admissionDate", "2026-01-05");
        map.put("classId", classId);
        return map;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body, RequestPostProcessor user)
            throws Exception {
        return mockMvc.perform(request.with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions admit(Map<String, Object> body) throws Exception {
        return send(post(URL), body, as("ADMIN"));
    }

    private long admitId(Map<String, Object> body) throws Exception {
        return idOf(admit(body).andExpect(status().isCreated()));
    }

    private ResultActions changeStatus(long id, String newStatus) throws Exception {
        return send(patch(URL + "/" + id + "/status"), Map.of("status", newStatus), as("ADMIN"));
    }

    /** Fast insert for search data. */
    private void insert(String admissionNo, String first, String last, String guardianPhone,
                        Long classId, Integer roll, String status) {
        jdbcTemplate.update("INSERT INTO students (admission_no, first_name, last_name, gender, date_of_birth,"
                + " guardian_name, guardian_phone, guardian_relation, school_class_id, roll_no, status,"
                + " admission_date, created_at, updated_at) VALUES (?, ?, ?, 'FEMALE', '2012-05-01', 'Guardian',"
                + " ?, 'Mother', ?, ?, ?, '2026-01-01', now(), now())",
                admissionNo, first, last, guardianPhone, classId, roll, status);
    }

    private static RequestPostProcessor asUser(String username, Role role) {
        return jwt().jwt(token -> token.subject(username))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // ---------- admit ----------

    @Test
    void admit_normalisesValues_andAssignsNextRollNo() throws Exception {
        long classId = createClass("Class 6", "A", 40);
        Map<String, Object> first = student(" adm-2026-101 ", classId);
        first.put("email", "Rahim@School.EDU");
        first.put("phone", "+8801711111111");

        admit(first)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.student.admissionNo").value("ADM-2026-101"))
                .andExpect(jsonPath("$.student.fullName").value("Rahim Uddin"))
                .andExpect(jsonPath("$.student.email").value("rahim@school.edu"))
                .andExpect(jsonPath("$.student.classId").value(classId))
                .andExpect(jsonPath("$.student.className").value("Class 6 - A"))
                .andExpect(jsonPath("$.student.rollNo").value(1))
                .andExpect(jsonPath("$.student.status").value("ACTIVE"))
                .andExpect(jsonPath("$.student.address").doesNotExist())
                .andExpect(jsonPath("$.student.username").doesNotExist())
                .andExpect(jsonPath("$.account").doesNotExist());
        admit(student("ADM-2026-102", classId)).andExpect(jsonPath("$.student.rollNo").value(2));

        Map<String, Object> chosenRoll = student("ADM-2026-103", classId);
        chosenRoll.put("rollNo", 10);
        admit(chosenRoll).andExpect(jsonPath("$.student.rollNo").value(10));
        admit(student("ADM-2026-104", classId)).andExpect(jsonPath("$.student.rollNo").value(11));

        assertThat(auditLogRepository.findAll()).extracting(entry -> entry.getAction())
                .contains("STUDENT_ADMITTED");
    }

    @Test
    void admit_withoutClass_hasNoRoll() throws Exception {
        Map<String, Object> body = student("ADM-1", null);
        body.put("rollNo", 5);

        admit(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.student.classId").doesNotExist())
                .andExpect(jsonPath("$.student.rollNo").doesNotExist());
    }

    @Test
    void duplicateAdmissionNo_returns409() throws Exception {
        admitId(student("ADM-1", null));

        admit(student(" adm-1", null))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Admission number ADM-1 already exists"));
    }

    @Test
    void duplicateEmail_returns409_blankEmailIsStoredAsNull() throws Exception {
        Map<String, Object> first = student("ADM-1", null);
        first.put("email", "rahim@school.edu");
        admitId(first);
        Map<String, Object> second = student("ADM-2", null);
        second.put("email", "RAHIM@school.edu");

        admit(second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email rahim@school.edu is already used by another student"));

        // Blank email is stored as null, so many students can have none (the column is UNIQUE).
        second.put("email", "");
        admit(second).andExpect(status().isCreated()).andExpect(jsonPath("$.student.email").doesNotExist());
        Map<String, Object> third = student("ADM-3", null);
        third.put("email", "");
        admit(third).andExpect(status().isCreated());
    }

    @Test
    void invalidInput_returns400WithFieldErrors() throws Exception {
        admit(Map.of("firstName", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.admissionNo").isNotEmpty())
                .andExpect(jsonPath("$.errors.firstName").isNotEmpty())
                .andExpect(jsonPath("$.errors.lastName").isNotEmpty())
                .andExpect(jsonPath("$.errors.gender").isNotEmpty())
                .andExpect(jsonPath("$.errors.dateOfBirth").isNotEmpty())
                .andExpect(jsonPath("$.errors.guardianName").isNotEmpty())
                .andExpect(jsonPath("$.errors.guardianPhone").isNotEmpty())
                .andExpect(jsonPath("$.errors.guardianRelation").isNotEmpty())
                .andExpect(jsonPath("$.errors.admissionDate").isNotEmpty());

        Map<String, Object> bad = student("ADM-1", null);
        bad.put("dateOfBirth", "2099-01-01");
        bad.put("admissionDate", "2099-01-01");
        bad.put("email", "not-an-email");
        bad.put("phone", "12345");
        bad.put("guardianPhone", "017-1111-1111");
        bad.put("rollNo", 0);
        admit(bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateOfBirth").isNotEmpty())
                .andExpect(jsonPath("$.errors.admissionDate").isNotEmpty())
                .andExpect(jsonPath("$.errors.email").isNotEmpty())
                .andExpect(jsonPath("$.errors.phone").value("must be 10-15 digits, optionally starting with +"))
                .andExpect(jsonPath("$.errors.guardianPhone").isNotEmpty())
                .andExpect(jsonPath("$.errors.rollNo").isNotEmpty());

        bad = student("ADM-1", null);
        bad.put("rollNo", 501);
        admit(bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.rollNo").isNotEmpty());
        bad = student("ADM-1", null);
        bad.put("gender", "UNKNOWN");
        admit(bad).andExpect(status().isBadRequest());
        bad = student("A".repeat(21), null);
        admit(bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.admissionNo").isNotEmpty());

        admit(student("ADM-1", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Class 999999 was not found"));
    }

    // ---------- placement ----------

    @Test
    void capacityBoundary_lastSeatAllowed_thenFull_andInactiveStudentsFreeTheirSeat() throws Exception {
        long classId = createClass("Class 6", "A", 2);
        long first = admitId(student("ADM-1", classId));
        admitId(student("ADM-2", classId));

        admit(student("ADM-3", classId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Class 6 - A is full (capacity 2)"));

        changeStatus(first, "TRANSFERRED").andExpect(status().isOk());
        admit(student("ADM-3", classId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.student.rollNo").value(3));

        changeStatus(first, "ACTIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Class 6 - A is full (capacity 2)"));
        mockMvc.perform(get("/api/v1/classes/" + classId).with(as("ADMIN")))
                .andExpect(jsonPath("$.studentCount").value(2));
    }

    @Test
    void rollRules_uniqueInClass_keptOnEdit_newOnMove_clearedWithoutClass() throws Exception {
        long classA = createClass("Class 6", "A", 40);
        long classB = createClass("Class 6", "B", 40);
        admitId(student("ADM-1", classA));
        long id = admitId(student("ADM-2", classA));

        Map<String, Object> clash = student("ADM-3", classA);
        clash.put("rollNo", 1);
        admit(clash)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Roll number 1 is already taken in Class 6 - A"));

        Map<String, Object> edit = student("CHANGED", classA);
        edit.put("firstName", "Karim");
        send(put(URL + "/" + id), edit, as("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admissionNo").value("ADM-2"))
                .andExpect(jsonPath("$.fullName").value("Karim Uddin"))
                .andExpect(jsonPath("$.rollNo").value(2));

        edit.put("rollNo", 1);
        send(put(URL + "/" + id), edit, as("ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Roll number 1 is already taken in Class 6 - A"));

        edit.put("rollNo", null);
        edit.put("classId", classB);
        send(put(URL + "/" + id), edit, as("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("Class 6 - B"))
                .andExpect(jsonPath("$.rollNo").value(1));

        edit.put("classId", null);
        send(put(URL + "/" + id), edit, as("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classId").doesNotExist())
                .andExpect(jsonPath("$.rollNo").doesNotExist());

        send(put(URL + "/999999"), edit, as("ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Student 999999 was not found"));
    }

    // ---------- soft delete ----------

    @Test
    void deactivating_aStudent_disablesTheirLogin_andKeepsTheRecord() throws Exception {
        long classId = createClass("Class 6", "A", 40);
        long id = admitId(student("ADM-9", classId));
        AppUser login = appUserRepository.save(
                new AppUser("adm-9", "adm-9@school.edu", "$2a$12$testHashPlaceholder", "Rahim Uddin", Role.STUDENT));
        jdbcTemplate.update("UPDATE students SET user_id = ? WHERE id = ?", login.getId(), id);
        entityManager.flush();
        entityManager.clear();

        // The linked login sees its student id in /auth/me (StudentProfileLookup).
        mockMvc.perform(get("/api/v1/auth/me").with(asUser("adm-9", Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(id));

        changeStatus(id, "GRADUATED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRADUATED"))
                .andExpect(jsonPath("$.username").value("adm-9"))
                .andExpect(jsonPath("$.className").value("Class 6 - A"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT enabled FROM users WHERE id = ?", Boolean.class, login.getId())).isFalse();
        mockMvc.perform(get("/api/v1/auth/me").with(asUser("adm-9", Role.STUDENT)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRADUATED"));
        mockMvc.perform(delete(URL + "/" + id).with(as("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
        assertThat(auditLogRepository.findAll()).extracting(entry -> entry.getAction())
                .contains("STUDENT_STATUS_CHANGED");

        send(patch(URL + "/" + id + "/status"), Map.of("status", "DELETED"), as("ADMIN"))
                .andExpect(status().isBadRequest());
        send(patch(URL + "/" + id + "/status"), Map.of(), as("ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").isNotEmpty());
        changeStatus(999999, "ACTIVE").andExpect(status().isNotFound());
    }

    // ---------- search ----------

    @Test
    void search_filtersByTextClassAndStatus_andPaginates() throws Exception {
        long classA = createClass("Class 6", "A", 40);
        long classB = createClass("Class 7", "A", 40);
        insert("ADM-003", "Nusrat", "Jahan", "01811111111", classA, 1, "ACTIVE");
        insert("ADM-001", "Rahim", "Uddin", "01711111111", classA, 2, "ACTIVE");
        insert("ADM-002", "Karim", "Rahman", "01922222222", classB, 1, "ACTIVE");
        insert("ADM-004", "Rahima", "Begum", "01633333333", classB, 2, "GRADUATED");
        insert("ADM-005", "Sadia", "Islam", "01544444444", null, null, "ACTIVE");

        // Default: page 0, size 20, ordered by admission no, class name included.
        mockMvc.perform(get(URL).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].admissionNo").value("ADM-001"))
                .andExpect(jsonPath("$.content[0].fullName").value("Rahim Uddin"))
                .andExpect(jsonPath("$.content[0].className").value("Class 6 - A"))
                .andExpect(jsonPath("$.content[4].admissionNo").value("ADM-005"))
                .andExpect(jsonPath("$.content[4].className").doesNotExist());

        // Part of the full name, any case: "rahim u" only matches "Rahim Uddin"; "RAHIM" also "Rahima Begum".
        mockMvc.perform(get(URL).param("q", "rahim u").with(as("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].admissionNo").value("ADM-001"));
        mockMvc.perform(get(URL).param("q", " RAHIM ").with(as("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(URL).param("q", "adm-00").with(as("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(5));
        mockMvc.perform(get(URL).param("q", "019222").with(as("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Karim Rahman"));
        mockMvc.perform(get(URL).param("q", "%").with(as("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get(URL).param("classId", String.valueOf(classB)).with(as("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(URL).param("classId", String.valueOf(classB)).param("status", "ACTIVE")
                        .with(as("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].admissionNo").value("ADM-002"));
        mockMvc.perform(get(URL).param("q", "rahim").param("status", "GRADUATED").with(as("ADMIN")))
                .andExpect(jsonPath("$.content[0].fullName").value("Rahima Begum"));

        mockMvc.perform(get(URL).param("page", "1").param("size", "2").with(as("ADMIN")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].admissionNo").value("ADM-003"));
        mockMvc.perform(get(URL).param("sort", "admissionNo,desc").param("size", "1").with(as("ADMIN")))
                .andExpect(jsonPath("$.content[0].admissionNo").value("ADM-005"));

        mockMvc.perform(get(URL).param("sort", "password").with(as("ADMIN"))).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("status", "LOST").with(as("ADMIN"))).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("classId", "abc").with(as("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void search_usesAFixedNumberOfQueries() throws Exception {
        long first = createClass("Class 6", "A", 40);
        for (int i = 1; i <= 6; i++) {
            insert("ADM-A" + i, "Student", "A" + i, "01711111111", first, i, "ACTIVE");
        }
        long fewClasses = countQueriesOfSearch();

        for (String section : new String[] {"B", "C", "D", "E", "F", "G"}) {
            long classId = createClass("Class 6", section, 40);
            insert("ADM-" + section, "Student", section, "01711111111", classId, 1, "ACTIVE");
        }
        long manyClasses = countQueriesOfSearch();

        // 1 AccountStateFilter user reload + 1 page select (classes joined in) + 1 count
        assertThat(manyClasses).isEqualTo(fewClasses).isLessThanOrEqualTo(3);
    }

    private long countQueriesOfSearch() throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            mockMvc.perform(get(URL).param("size", "5").param("sort", "admissionNo,desc").with(as("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].className").isNotEmpty());
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    // ---------- roster and classes ----------

    @Test
    void roster_listsActiveStudentsByRoll_andClassesCountOnlyActiveStudents() throws Exception {
        long classId = createClass("Class 6", "A", 40);
        insert("ADM-1", "Third", "Roll", "01711111111", classId, 3, "ACTIVE");
        insert("ADM-2", "First", "Roll", "01711111111", classId, 1, "ACTIVE");
        insert("ADM-3", "Gone", "Away", "01711111111", classId, 2, "TRANSFERRED");

        mockMvc.perform(get("/api/v1/classes/" + classId + "/students").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rollNo").value(1))
                .andExpect(jsonPath("$[0].fullName").value("First Roll"))
                .andExpect(jsonPath("$[1].rollNo").value(3))
                .andExpect(jsonPath("$[1].className").value("Class 6 - A"));
        mockMvc.perform(get("/api/v1/classes/999999/students").with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Class 999999 was not found"));

        mockMvc.perform(get("/api/v1/classes").with(as("ADMIN")))
                .andExpect(jsonPath("$[0].studentCount").value(2));
        String body = objectMapper.writeValueAsString(Map.of(
                "academicYearId", yearId, "name", "Class 6", "section", "A", "capacity", 1));
        mockMvc.perform(put("/api/v1/classes/" + classId).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Capacity cannot be lower than the 2 students already enrolled"));
        mockMvc.perform(delete("/api/v1/classes/" + classId).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Move the students out of Class 6 - A before deleting it"));
    }

    // ---------- export ----------

    @Test
    void export_returnsCsv_withFiltersAndFormulaDefence() throws Exception {
        long classId = createClass("Class 6", "A", 40);
        Map<String, Object> risky = student("ADM-2", classId);
        risky.put("firstName", "=HYPERLINK(\"http://x\")");
        risky.put("lastName", "Khan, Jr");
        admitId(risky);
        admitId(student("ADM-1", null));

        MvcResult all = mockMvc.perform(get(URL + "/export").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"students.csv\""))
                .andReturn();
        List<String> lines = List.of(all.getResponse().getContentAsString().split("\r\n"));
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo(StudentCsvExporter.HEADER);
        assertThat(lines.get(1)).startsWith("\"ADM-1\",\"Rahim\",\"Uddin\",\"MALE\",\"2014-03-09\",\"\",\"\",\"ACTIVE\"");
        assertThat(lines.get(2)).startsWith(
                "\"ADM-2\",\"'=HYPERLINK(\"\"http://x\"\")\",\"Khan, Jr\",\"MALE\",\"2014-03-09\",\"Class 6 - A\",\"1\"");

        MvcResult filtered = mockMvc.perform(get(URL + "/export").param("classId", String.valueOf(classId))
                        .param("status", "ACTIVE").with(as("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        assertThat(filtered.getResponse().getContentAsString().split("\r\n")).hasSize(2);
    }

    // ---------- roles ----------

    @Test
    void everyEndpoint_adminAllowed_teacherAndStudentDenied_anonymousUnauthorized() throws Exception {
        long classId = createClass("Class 6", "A", 40);
        long id = admitId(student("ADM-1", classId));

        mockMvc.perform(get(URL).with(as("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get(URL + "/export").with(as("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/classes/" + classId + "/students").with(as("ADMIN")))
                .andExpect(status().isOk());

        for (String role : new String[] {"TEACHER", "STUDENT"}) {
            mockMvc.perform(get(URL).with(as(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mockMvc.perform(get(URL + "/export").with(as(role))).andExpect(status().isForbidden());
            mockMvc.perform(get(URL + "/" + id).with(as(role))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/classes/" + classId + "/students").with(as(role)))
                    .andExpect(status().isForbidden());
            send(post(URL), student("ADM-X", null), as(role)).andExpect(status().isForbidden());
            send(put(URL + "/" + id), student("ADM-1", classId), as(role)).andExpect(status().isForbidden());
            send(patch(URL + "/" + id + "/status"), Map.of("status", "INACTIVE"), as(role))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/classes/" + classId + "/students")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(student("ADM-X", null))))
                .andExpect(status().isUnauthorized());

        // Nothing was changed by the denied calls.
        mockMvc.perform(get(URL + "/" + id).with(as("ADMIN")))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get(URL).with(as("ADMIN"))).andExpect(jsonPath("$.totalElements").value(1));
    }
}
