package com.school.academic;

import com.school.IntegrationTestBase;
import com.school.audit.AuditLogRepository;
import com.school.security.AccessGuard;
import com.school.security.TeachingScope;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.school.academic.AcademicYearIntegrationTest.as;
import static com.school.academic.AcademicYearIntegrationTest.seedTestUsers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassSubjectIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AccessGuard accessGuard;

    @Autowired
    private TeachingScope teachingScope;

    private Teacher classTeacher;
    private Teacher subjectTeacher;
    private Teacher unrelatedTeacher;

    @BeforeEach
    void setUp() {
        seedTestUsers(appUserRepository);
        classTeacher = createTeacherWithAccount("t-class", "Class", "Teacher", "classteacher@school.edu");
        subjectTeacher = createTeacherWithAccount("t-subject", "Subject", "Teacher", "subjectteacher@school.edu");
        unrelatedTeacher = createTeacherWithAccount("t-other", "Other", "Teacher", "otherteacher@school.edu");
    }

    private Teacher createTeacherWithAccount(String username, String first, String last, String email) {
        AppUser user = appUserRepository.save(new AppUser(
                username, email, "$2a$12$testHashPlaceholder", first + " " + last, Role.TEACHER));
        return teacherRepository.save(new Teacher(
                user, username.toUpperCase(), first, last, email,
                "+8801711111111", "Lecturer", LocalDate.of(2025, 1, 1)));
    }

    private static RequestPostProcessor asUser(String username, String role) {
        return jwt().jwt(t -> t.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private long createYear() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/academic-years").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "2026", "startDate", "2026-01-01", "endDate", "2026-12-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createClass(long yearId, String name, String section, Long classTeacherId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("academicYearId", yearId);
        body.put("name", name);
        body.put("section", section);
        body.put("capacity", 40);
        body.put("classTeacherId", classTeacherId);
        MvcResult res = mockMvc.perform(post("/api/v1/classes").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createSubject(String code, String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code, "name", name, "fullMarks", 100, "passMarks", 33))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private String assignBody(Long subjectId, Long teacherId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("subjectId", subjectId);
        body.put("teacherId", teacherId);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void assignSubjectToClass_withTeacher() throws Exception {
        long yearId = createYear();
        long classId = createClass(yearId, "Class 9", "A", classTeacher.getId());
        long phyId = createSubject("PHY101", "Physics");
        long banId = createSubject("BAN101", "Bangla");

        // Assign PHY101 with teacher
        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(phyId, subjectTeacher.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.subjectId").value(phyId))
                .andExpect(jsonPath("$.subjectCode").value("PHY101"))
                .andExpect(jsonPath("$.subjectName").value("Physics"))
                .andExpect(jsonPath("$.fullMarks").value(100))
                .andExpect(jsonPath("$.passMarks").value(33))
                .andExpect(jsonPath("$.teacherId").value(subjectTeacher.getId()))
                .andExpect(jsonPath("$.teacherName").value("Subject Teacher"));

        // Assign BAN101 with null teacher
        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(banId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectCode").value("BAN101"))
                .andExpect(jsonPath("$.teacherId").doesNotExist())
                .andExpect(jsonPath("$.teacherName").doesNotExist());

        // List is ordered by subject code: BAN101 before PHY101
        mockMvc.perform(get("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subjectCode").value("BAN101"))
                .andExpect(jsonPath("$[1].subjectCode").value("PHY101"));

        // Change PHY101 teacher to null, then to classTeacher
        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(phyId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").doesNotExist());

        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(phyId, classTeacher.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(classTeacher.getId()))
                .andExpect(jsonPath("$.teacherName").value("Class Teacher"));

        // Remove BAN101
        mockMvc.perform(delete("/api/v1/classes/" + classId + "/subjects/" + banId).with(as("ADMIN")))
                .andExpect(status().isNoContent());

        // Removing again -> 404 exact message
        mockMvc.perform(delete("/api/v1/classes/" + classId + "/subjects/" + banId).with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This subject is not assigned to the class"));

        assertThat(auditLogRepository.findAll())
                .extracting(entry -> entry.getAction())
                .contains("SUBJECT_ASSIGNED");
    }

    @Test
    void ownershipAndRoleRules_forClassSubjectsAndGuards() throws Exception {
        long yearId = createYear();
        long classId = createClass(yearId, "Class 10", "A", classTeacher.getId());
        long phyId = createSubject("PHY101", "Physics");
        long matId = createSubject("MAT101", "Mathematics");

        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(phyId, subjectTeacher.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(matId, null)))
                .andExpect(status().isOk());

        // GET allowed for ADMIN, classTeacher, subjectTeacher
        mockMvc.perform(get("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/classes/" + classId + "/subjects").with(asUser("t-class", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/classes/" + classId + "/subjects").with(asUser("t-subject", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Unrelated teacher -> 403 exact message
        mockMvc.perform(get("/api/v1/classes/" + classId + "/subjects").with(asUser("t-other", "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You do not teach in this class"));

        // Teacher user with no Teacher profile -> 403 exact message
        mockMvc.perform(get("/api/v1/classes/" + classId + "/subjects").with(as("TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No teacher profile is linked to this account"));

        // Student -> 403; anonymous -> 401
        mockMvc.perform(get("/api/v1/classes/" + classId + "/subjects").with(as("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/classes/" + classId + "/subjects"))
                .andExpect(status().isUnauthorized());

        // PUT/DELETE denied for TEACHER (even classTeacher) & STUDENT
        for (RequestPostProcessor user : List.of(asUser("t-class", "TEACHER"), as("STUDENT"))) {
            mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(user)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(assignBody(matId, subjectTeacher.getId())))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/classes/" + classId + "/subjects/" + phyId).with(user))
                    .andExpect(status().isForbidden());
        }

        // Verify TeachingScope and AccessGuard.checkCanGradeSubject against real DB
        assertThat(teachingScope.accessibleClassIds("t-class")).containsExactly(classId);
        assertThat(teachingScope.accessibleClassIds("t-subject")).containsExactly(classId);
        assertThat(teachingScope.accessibleClassIds("t-other")).isEmpty();

        try {
            // subjectTeacher can grade PHY101, not MAT101
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "t-subject", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
            assertThatCode(() -> accessGuard.checkCanGradeSubject(classId, phyId)).doesNotThrowAnyException();
            assertThatThrownBy(() -> accessGuard.checkCanGradeSubject(classId, matId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("This subject is not assigned to you in this class");

            // classTeacher without subject assignment cannot grade PHY101
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "t-class", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
            assertThatThrownBy(() -> accessGuard.checkCanGradeSubject(classId, phyId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("This subject is not assigned to you in this class");

            // Admin passes checkCanGradeSubject
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertThatCode(() -> accessGuard.checkCanGradeSubject(classId, matId)).doesNotThrowAnyException();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void validationAnd404s_returnExactProblemJson() throws Exception {
        long yearId = createYear();
        long classId = createClass(yearId, "Class 8", "A", null);
        long subjectId = createSubject("CHE101", "Chemistry");

        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.subjectId").isNotEmpty());

        mockMvc.perform(get("/api/v1/classes/999999/subjects").with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Class 999999 was not found"));

        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(999999L, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Subject 999999 was not found"));

        mockMvc.perform(put("/api/v1/classes/" + classId + "/subjects").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(subjectId, 999999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Teacher 999999 was not found"));
    }
}
