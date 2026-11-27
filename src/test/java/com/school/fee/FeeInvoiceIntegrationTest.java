package com.school.fee;

import com.school.IntegrationTestBase;
import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.academic.SubjectRepository;
import com.school.audit.AuditLogRepository;
import com.school.student.Gender;
import com.school.student.Student;
import com.school.student.StudentRepository;
import com.school.student.StudentStatus;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeeInvoiceIntegrationTest extends IntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedDhakaClock() {
            // 00:30 on 26 Nov 2026 in Asia/Dhaka (still 25 Nov in UTC).
            return Clock.fixed(Instant.parse("2026-11-25T18:30:00Z"), ZoneId.of("Asia/Dhaka"));
        }
    }

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private ClassSubjectRepository classSubjectRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private SchoolClass class9A;
    private SchoolClass class9B;
    private Student amina;
    private Student babul;
    private Student chandan9B;
    private Student inactiveStudent;
    private String adminToken;
    private String teacherToken;
    private String aminaToken;
    private String babulToken;

    @BeforeEach
    void setUp() throws Exception {
        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN));

        AppUser teacherUser = appUserRepository.save(new AppUser(
                "t-1001", "t1001@school.edu", passwordEncoder.encode("TeacherPass#1"), "Ayesha Rahman", Role.TEACHER));
        Teacher classTeacher = teacherRepository.save(new Teacher(
                teacherUser, "T-1001", "Ayesha", "Rahman", "t1001@school.edu",
                "+8801711000001", "Senior Teacher", LocalDate.of(2025, 1, 1)));

        AppUser aminaUser = appUserRepository.save(new AppUser(
                "adm-2026-001", "amina@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam", Role.STUDENT));
        AppUser babulUser = appUserRepository.save(new AppUser(
                "adm-2026-002", "babul@school.edu", passwordEncoder.encode("StudentPass#1"), "Babul Mia", Role.STUDENT));

        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);

        class9A = schoolClassRepository.save(new SchoolClass(year, "Class 9", "A", "101", 40, classTeacher));
        class9B = schoolClassRepository.save(new SchoolClass(year, "Class 9", "B", "102", 40, null));
        Subject math = subjectRepository.save(new Subject("MATH", "Mathematics", 100, 33));
        classSubjectRepository.save(new ClassSubject(class9A, math, classTeacher));

        amina = saveStudent("ADM-2026-001", "Amina", "Islam", class9A, 1, StudentStatus.ACTIVE, aminaUser);
        babul = saveStudent("ADM-2026-002", "Babul", "Mia", class9A, 2, StudentStatus.ACTIVE, babulUser);
        chandan9B = saveStudent("ADM-2026-003", "Chandan", "Roy", class9B, 1, StudentStatus.ACTIVE, null);
        inactiveStudent = saveStudent("ADM-2026-004", "Farhana", "Akter", class9A, 3, StudentStatus.GRADUATED, null);

        adminToken = login("admin", "AdminPass#1");
        teacherToken = login("t-1001", "TeacherPass#1");
        aminaToken = login("adm-2026-001", "StudentPass#1");
        babulToken = login("adm-2026-002", "StudentPass#1");
    }

    private Student saveStudent(
            String admissionNo,
            String firstName,
            String lastName,
            SchoolClass schoolClass,
            int roll,
            StudentStatus status,
            AppUser user
    ) {
        Student s = new Student(admissionNo);
        s.setUser(user);
        s.setFirstName(firstName);
        s.setLastName(lastName);
        s.setGender(Gender.FEMALE);
        s.setDateOfBirth(LocalDate.of(2012, 5, 10));
        s.setGuardianName("Parent " + firstName);
        s.setGuardianPhone("0170000000" + roll);
        s.setGuardianRelation("Mother");
        s.setSchoolClass(schoolClass);
        s.setRollNo(roll);
        s.setStatus(status);
        s.setAdmissionDate(LocalDate.of(2026, 1, 5));
        return studentRepository.save(s);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void create_validatesMoneyBoundariesAndActiveStudent_andCreatesInvoice() throws Exception {
        // Below minimum amount -> 400
        mockMvc.perform(post("/api/v1/fees/invoices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", amina.getId(),
                                "title", "Tuition",
                                "amount", "0.99",
                                "dueDate", TODAY.toString()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());

        // Above maximum amount -> 400
        mockMvc.perform(post("/api/v1/fees/invoices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", amina.getId(),
                                "title", "Tuition",
                                "amount", "1000000.01",
                                "dueDate", TODAY.toString()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());

        // Discount larger than amount -> 422
        mockMvc.perform(post("/api/v1/fees/invoices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", amina.getId(),
                                "title", "Tuition",
                                "amount", "500.00",
                                "discount", "500.01",
                                "dueDate", TODAY.toString()
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Discount cannot be larger than the amount"));

        // Inactive student -> 422
        mockMvc.perform(post("/api/v1/fees/invoices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", inactiveStudent.getId(),
                                "title", "Tuition",
                                "amount", "500.00",
                                "dueDate", TODAY.toString()
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Invoices can only be created for active students"));

        // Missing student -> 404
        mockMvc.perform(post("/api/v1/fees/invoices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", 999999L,
                                "title", "Tuition",
                                "amount", "500.00",
                                "dueDate", TODAY.toString()
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Student 999999 was not found"));

        // Exact min boundary 1.00 and full discount 1.00 -> 201
        mockMvc.perform(post("/api/v1/fees/invoices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", amina.getId(),
                                "title", "Minimum Fee",
                                "amount", "1.00",
                                "discount", "1.00",
                                "dueDate", TODAY.toString()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNo").value(org.hamcrest.Matchers.matchesPattern("^INV-202611-[A-HJ-NP-Z2-9]{6}$")))
                .andExpect(jsonPath("$.payable").value(0.00))
                .andExpect(jsonPath("$.outstanding").value(0.00))
                .andExpect(jsonPath("$.overdue").value(false));

        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> FeeInvoiceService.AUDIT_INVOICE_CREATED.equals(log.getAction()));
    }

    @Test
    void bulkCreate_twiceIsSafeAndSkipsCaseInsensitive_whileCancelledAllowsRecreate() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "title", "Tuition Fee - November 2026",
                "amount", "2000.00",
                "dueDate", TODAY.plusDays(5).toString()
        ));

        // First bulk creates for active Amina & Babul (ignores graduated student)
        mockMvc.perform(post("/api/v1/fees/invoices/bulk")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.skipped").value(0));

        // Repeating with different casing skips both active students
        String repeatPayload = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "title", "tuition fee - november 2026",
                "amount", "2000.00",
                "dueDate", TODAY.plusDays(5).toString()
        ));
        mockMvc.perform(post("/api/v1/fees/invoices/bulk")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(repeatPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(2));

        // Cancel Amina's invoice, then bulk again -> only Amina recreated (created=1, skipped=1)
        FeeInvoice aminaInvoice = feeInvoiceRepository.findAllByStudentIdWithDetails(amina.getId()).get(0);
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/cancel", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/fees/invoices/bulk")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(1));

        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> FeeInvoiceService.AUDIT_INVOICES_BULK_CREATED.equals(log.getAction()))
                .anyMatch(log -> FeeInvoiceService.AUDIT_INVOICE_CANCELLED.equals(log.getAction()));
    }

    @Test
    void search_overdueBoundary_andSummary_andNPlusOneFree() throws Exception {
        // Due yesterday -> overdue
        FeeInvoice yesterdayUnpaid = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-AAA222", amina, "Exam Fee",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), TODAY.minusDays(1)));
        // Due today in Dhaka -> NOT overdue
        FeeInvoice todayPartial = new FeeInvoice(
                "INV-202611-BBB333", babul, "Lab Fee",
                new BigDecimal("600.00"), BigDecimal.ZERO, TODAY);
        todayPartial.applyPayment(new BigDecimal("200.00"));
        feeInvoiceRepository.save(todayPartial);
        // Cancelled -> excluded from billed and overdue
        FeeInvoice cancelledOld = new FeeInvoice(
                "INV-202611-CCC444", chandan9B, "Library Fee",
                new BigDecimal("500.00"), BigDecimal.ZERO, TODAY.minusDays(10));
        cancelledOld.cancel();
        feeInvoiceRepository.save(cancelledOld);

        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        // Default sort: newest dueDate first (todayPartial before yesterdayUnpaid before cancelledOld)
        mockMvc.perform(get("/api/v1/fees/invoices")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].invoiceNo").value("INV-202611-BBB333"))
                .andExpect(jsonPath("$.content[0].overdue").value(false))
                .andExpect(jsonPath("$.content[1].invoiceNo").value("INV-202611-AAA222"))
                .andExpect(jsonPath("$.content[1].overdue").value(true));

        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(2);

        // Overdue filter
        mockMvc.perform(get("/api/v1/fees/invoices")
                        .param("overdue", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(yesterdayUnpaid.getId()));

        // Search by student name, admission no, invoice no, classId, status
        mockMvc.perform(get("/api/v1/fees/invoices")
                        .param("q", "amina islam")
                        .param("classId", class9A.getId().toString())
                        .param("status", "UNPAID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].studentName").value("Amina Islam"));

        // Summary: billed = 900 + 600 = 1500.00 (cancelled 500 excluded), collected = 200.00, outstanding = 1300.00, rate = 13.3
        mockMvc.perform(get("/api/v1/fees/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBilled").value(1500.00))
                .andExpect(jsonPath("$.totalCollected").value(200.00))
                .andExpect(jsonPath("$.totalOutstanding").value(1300.00))
                .andExpect(jsonPath("$.overdueInvoices").value(1))
                .andExpect(jsonPath("$.collectionRate").value(13.3));

        // Cancel invoice with payments -> 409
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/cancel", todayPartial.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Invoice INV-202611-BBB333 already has payments and cannot be cancelled"));
    }

    @Test
    void rolesAndOwnership_teacherAlways403_studentOnlyOwn() throws Exception {
        FeeInvoice aminaInvoice = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-DDD555", amina, "Sports Fee",
                new BigDecimal("300.00"), BigDecimal.ZERO, TODAY));

        // Anonymous -> 401
        mockMvc.perform(get("/api/v1/fees/invoices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/fees/invoices/{id}", aminaInvoice.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/fees/students/{id}/invoices", amina.getId())).andExpect(status().isUnauthorized());

        // Teacher (even class teacher & subject teacher of Class 9-A) -> 403 on EVERY fee endpoint
        mockMvc.perform(get("/api/v1/fees/invoices").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fees/invoices/{id}", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fees/invoices/{id}", 999999L)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fees/students/{id}/invoices", amina.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fees/summary").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        String validInvoiceJson = objectMapper.writeValueAsString(Map.of(
                "studentId", amina.getId(),
                "title", "Sports Fee",
                "amount", "300.00",
                "dueDate", TODAY.toString()
        ));
        String validBulkJson = objectMapper.writeValueAsString(Map.of(
                "classId", class9A.getId(),
                "title", "Sports Fee",
                "amount", "300.00",
                "dueDate", TODAY.toString()
        ));
        mockMvc.perform(post("/api/v1/fees/invoices")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInvoiceJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/fees/invoices/bulk")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBulkJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/cancel", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        // Student: allowed own invoice & own student invoices -> 200
        mockMvc.perform(get("/api/v1/fees/invoices/{id}", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNo").value("INV-202611-DDD555"));
        mockMvc.perform(get("/api/v1/fees/students/{id}/invoices", amina.getId())
                        .header("Authorization", "Bearer " + aminaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Student: denied admin-only endpoints and other student's records -> 403
        mockMvc.perform(get("/api/v1/fees/invoices").header("Authorization", "Bearer " + aminaToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fees/summary").header("Authorization", "Bearer " + aminaToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fees/invoices/{id}", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + babulToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fees/students/{id}/invoices", amina.getId())
                        .header("Authorization", "Bearer " + babulToken))
                .andExpect(status().isForbidden());

        // Admin can read student's invoices -> 200, missing student -> 404
        mockMvc.perform(get("/api/v1/fees/students/{id}/invoices", amina.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/v1/fees/students/{id}/invoices", 999999L)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Student 999999 was not found"));
    }
}
