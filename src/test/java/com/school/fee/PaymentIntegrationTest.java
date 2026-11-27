package com.school.fee;

import com.school.IntegrationTestBase;
import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentIntegrationTest extends IntegrationTestBase {

    private static final LocalDate DUE = LocalDate.of(2026, 12, 15);

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private FeeInvoice aminaInvoice;
    private FeeInvoice babulInvoice;
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

        SchoolClass class9A = schoolClassRepository.save(
                new SchoolClass(year, "Class 9", "A", "101", 40, classTeacher));

        Student amina = saveStudent("ADM-2026-001", "Amina", "Islam", class9A, 1, aminaUser);
        Student babul = saveStudent("ADM-2026-002", "Babul", "Mia", class9A, 2, babulUser);

        aminaInvoice = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-AAA111", amina, "Tuition Fee",
                new BigDecimal("3000.00"), BigDecimal.ZERO, DUE));
        babulInvoice = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-BBB222", babul, "Lab Fee",
                new BigDecimal("1200.00"), BigDecimal.ZERO, DUE));

        adminToken = login("admin", "AdminPass#1");
        teacherToken = login("t-1001", "TeacherPass#1");
        aminaToken = login("adm-2026-001", "StudentPass#1");
        babulToken = login("adm-2026-002", "StudentPass#1");
    }

    private Student saveStudent(
            String admissionNo, String firstName, String lastName, SchoolClass schoolClass, int roll, AppUser user) {
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
        s.setStatus(StudentStatus.ACTIVE);
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
    void recordPayment_eachGateway_updatesInvoiceAndHistoryWithoutNPlusOne() throws Exception {
        // 1. CASH partial payment
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "idem-cash-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "1000.00",
                                "method", "CASH"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("CASH"))
                .andExpect(jsonPath("$.transactionRef").value(matchesPattern("^CASH-\\d{8}-\\d{6}$")))
                .andExpect(jsonPath("$.receivedBy").value("admin"))
                .andExpect(jsonPath("$.invoiceStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.invoiceOutstanding").value(2000.00));

        // 2. BKASH partial payment
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "idem-bkash-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "500.00",
                                "method", "BKASH"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("BKASH"))
                .andExpect(jsonPath("$.transactionRef").value(startsWith("BK")))
                .andExpect(jsonPath("$.invoiceStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.invoiceOutstanding").value(1500.00));

        // 3. BANK_TRANSFER final payment
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "idem-bank-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "1500.00",
                                "method", "BANK_TRANSFER",
                                "reference", "  BEFTN-2026-9001  "
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.transactionRef").value("BEFTN-2026-9001"))
                .andExpect(jsonPath("$.invoiceStatus").value("PAID"))
                .andExpect(jsonPath("$.invoiceOutstanding").value(0.00));

        // Replay of the same key on the same invoice (even after PAID) returns first payment without double-charging
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "idem-cash-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "1000.00",
                                "method", "CASH"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("CASH"))
                .andExpect(jsonPath("$.amount").value(1000.00));

        assertThat(paymentRepository.count()).isEqualTo(3);
        assertThat(auditLogRepository.findAll())
                .filteredOn(log -> PaymentService.AUDIT_PAYMENT_RECEIVED.equals(log.getAction()))
                .hasSize(3);

        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        // Admin reads payment history -> 3 rows, newest first, <= 3 SQL statements (AccountStateFilter + exists + join fetch)
        mockMvc.perform(get("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].transactionRef").value("BEFTN-2026-9001"))
                .andExpect(jsonPath("$[2].method").value("CASH"));

        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void recordPayment_validatesHeadersBodyAndConflicts() throws Exception {
        // Missing Idempotency-Key -> 422
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "100.00",
                                "method", "CASH"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The Idempotency-Key header is required for payments"));

        // Over-long Idempotency-Key -> 422
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "k".repeat(81))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "100.00",
                                "method", "CASH"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Idempotency-Key is too long (max 80)"));

        // Invalid amount -> 400
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "k-bad-amt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "0.00",
                                "method", "CASH"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());

        // Unknown payment method -> 400
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "k-bad-method")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"method\":\"CRYPTO\"}"))
                .andExpect(status().isBadRequest());

        // Bank transfer without reference -> 422
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "k-bank-noref")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "100.00",
                                "method", "BANK_TRANSFER"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Bank transaction reference is required"));

        // Pay on Amina's invoice, then reuse same key on Babul's invoice -> 409
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "k-cross-invoice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "100.00",
                                "method", "CASH"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", babulInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "k-cross-invoice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "100.00",
                                "method", "CASH"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This Idempotency-Key was already used for a different invoice"));

        // Missing invoice -> 404
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "k-missing-inv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "100.00",
                                "method", "CASH"
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rolesAndOwnership_everyEndpointEnforcesPermissions() throws Exception {
        String paymentBody = objectMapper.writeValueAsString(Map.of(
                "amount", "100.00",
                "method", "CASH"
        ));

        // Anonymous -> 401
        mockMvc.perform(get("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Idempotency-Key", "anon-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody))
                .andExpect(status().isUnauthorized());

        // Teacher -> 403 on both GET and POST
        mockMvc.perform(get("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("Idempotency-Key", "teacher-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody))
                .andExpect(status().isForbidden());

        // Student -> 403 on POST (even own invoice)
        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "student-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody))
                .andExpect(status().isForbidden());

        // Student -> 200 on own invoice payments, 403 on other student's or unknown invoice
        mockMvc.perform(get("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/fees/invoices/{id}/payments", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + babulToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/fees/invoices/{id}/payments", 999999L)
                        .header("Authorization", "Bearer " + aminaToken))
                .andExpect(status().isForbidden());

        // Admin -> 404 on unknown invoice
        mockMvc.perform(get("/api/v1/fees/invoices/{id}/payments", 999999L)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
