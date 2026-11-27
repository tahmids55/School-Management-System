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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeeIntegrationTest extends IntegrationTestBase {

    private static final LocalDate DUE = LocalDate.of(2026, 12, 20);

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

    private SchoolClass class9A;
    private Student amina;
    private Student babul;
    private FeeInvoice aminaInvoice;
    private FeeInvoice aminaLargeInvoice;
    private FeeInvoice babulInvoice;
    private String adminToken;
    private String teacherToken;
    private String aminaToken;
    private String babulToken;
    private String unlinkedStudentToken;

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
        appUserRepository.save(new AppUser(
                "no-profile", "noprofile@school.edu", passwordEncoder.encode("StudentPass#1"), "No Profile", Role.STUDENT));

        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);

        class9A = schoolClassRepository.save(new SchoolClass(year, "Class 9", "A", "101", 40, classTeacher));

        amina = saveStudent("ADM-2026-001", "Amina", "Islam", class9A, 1, aminaUser);
        babul = saveStudent("ADM-2026-002", "Babul", "Mia", class9A, 2, babulUser);

        aminaInvoice = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-F27A01", amina, "Tuition Fee",
                new BigDecimal("3000.00"), BigDecimal.ZERO, DUE));
        aminaLargeInvoice = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-F27A02", amina, "Annual Boarding Fee",
                new BigDecimal("60000.00"), BigDecimal.ZERO, DUE));
        babulInvoice = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-F27B01", babul, "Lab Fee",
                new BigDecimal("1500.00"), BigDecimal.ZERO, DUE));

        adminToken = login("admin", "AdminPass#1");
        teacherToken = login("t-1001", "TeacherPass#1");
        aminaToken = login("adm-2026-001", "StudentPass#1");
        babulToken = login("adm-2026-002", "StudentPass#1");
        unlinkedStudentToken = login("no-profile", "StudentPass#1");
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
    void samePaymentSentTwice_isChargedOnce() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("amount", "1200.00"));

        MvcResult first = mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "double-click-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("BKASH"))
                .andExpect(jsonPath("$.amount").value(1200.00))
                .andExpect(jsonPath("$.invoiceStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.invoiceOutstanding").value(1800.00))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "double-click-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(paymentRepository.count()).isEqualTo(1);
        FeeInvoice updated = feeInvoiceRepository.findById(aminaInvoice.getId()).orElseThrow();
        assertThat(updated.getAmountPaid()).isEqualByComparingTo("1200.00");
        assertThat(updated.outstanding()).isEqualByComparingTo("1800.00");
    }

    @Test
    void overpayment_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "overpay-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "3000.01"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Payment of 3000.01 exceeds the outstanding 3000.00"));

        assertThat(paymentRepository.count()).isZero();
        FeeInvoice unchanged = feeInvoiceRepository.findById(aminaInvoice.getId()).orElseThrow();
        assertThat(unchanged.getAmountPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    void bulkInvoicing_skipsStudentsWhoAlreadyHaveTheInvoice() throws Exception {
        feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-BULK01", amina, "Exam Fee - December",
                new BigDecimal("800.00"), BigDecimal.ZERO, DUE));

        mockMvc.perform(post("/api/v1/fees/invoices/bulk")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "classId", class9A.getId(),
                                "title", "Exam Fee - December",
                                "amount", "800.00",
                                "dueDate", DUE.toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void student_paysOwnInvoiceWithBkash() throws Exception {
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "own-bkash-full-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "3000.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceId").value(aminaInvoice.getId()))
                .andExpect(jsonPath("$.invoiceNo").value("INV-202611-F27A01"))
                .andExpect(jsonPath("$.amount").value(3000.00))
                .andExpect(jsonPath("$.method").value("BKASH"))
                .andExpect(jsonPath("$.transactionRef").value(startsWith("BK")))
                .andExpect(jsonPath("$.receivedBy").value("adm-2026-001"))
                .andExpect(jsonPath("$.invoiceStatus").value("PAID"))
                .andExpect(jsonPath("$.invoiceOutstanding").value(0.00));

        FeeInvoice paid = feeInvoiceRepository.findById(aminaInvoice.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.outstanding()).isEqualByComparingTo("0.00");
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> PaymentService.AUDIT_PAYMENT_RECEIVED.equals(log.getAction()));
    }

    @Test
    void student_cannotPaySomeoneElsesInvoice() throws Exception {
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", babulInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "other-student-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "500.00"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You can only pay your own invoices"));

        // Non-existent invoice returns the same 403 to prevent invoice ID enumeration
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", 999999L)
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "missing-inv-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "500.00"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can only pay your own invoices"));

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void bkashDeclinesAboveSandboxLimit_with402() throws Exception {
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaLargeInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "above-limit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "50000.01"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Transaction limit exceeded"));

        FeeInvoice unchanged = feeInvoiceRepository.findById(aminaLargeInvoice.getId()).orElseThrow();
        assertThat(unchanged.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(unchanged.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(paymentRepository.count()).isZero();
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> PaymentService.AUDIT_PAYMENT_DECLINED.equals(log.getAction()));
    }

    @Test
    void roleAndInputValidation_enforcesStudentOnlyAndContracts() throws Exception {
        String validBody = objectMapper.writeValueAsString(Map.of("amount", "100.00"));

        // Anonymous -> 401
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Idempotency-Key", "anon-self-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());

        // Admin and Teacher -> 403
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "admin-self-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("Idempotency-Key", "teacher-self-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        // Student without linked student profile -> 403
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + unlinkedStudentToken)
                        .header("Idempotency-Key", "unlinked-self-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        // Missing Idempotency-Key -> 422
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("The Idempotency-Key header is required for payments"));

        // Invalid amount -> 400
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "bad-amt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "0.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());

        // Cross-invoice Idempotency-Key reuse -> 409
        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "shared-self-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/me/invoices/{invoiceId}/pay", aminaLargeInvoice.getId())
                        .header("Authorization", "Bearer " + aminaToken)
                        .header("Idempotency-Key", "shared-self-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This Idempotency-Key was already used for a different invoice"));
    }
}
