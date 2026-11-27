package com.school.notification;

import com.school.CommittedIntegrationTestBase;
import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.exam.Exam;
import com.school.exam.ExamRepository;
import com.school.exam.ResultsPublishedEvent;
import com.school.fee.PaymentReceivedEvent;
import com.school.notice.NoticeAudience;
import com.school.notice.NoticePublishedEvent;
import com.school.notification.sms.FakeSmsGatewayClient;
import com.school.notification.sms.FakeSmsGatewayClient.SentSms;
import com.school.student.Gender;
import com.school.student.Student;
import com.school.student.StudentRepository;
import com.school.student.StudentStatus;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The events are published inside a REAL transaction that either commits or rolls back
 * (no test transaction around it), so the AFTER_COMMIT listener behaves exactly as in production.
 * F29 is built in parallel, so ResultsPublishedEvent is published directly here.
 * No bean is mocked, so this test shares the Spring context of the other committed tests;
 * SMS are checked in the fake provider's outbox (behind the real adapter).
 */
class NotificationEventsCommittedIntegrationTest extends CommittedIntegrationTestBase {

    @Autowired
    private FakeSmsGatewayClient smsProvider;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private TransactionTemplate transaction;
    private AppUser admin;
    private AppUser teacher;
    private AppUser disabledTeacher;
    private AppUser aminaLogin;
    private AppUser oldYearLogin;
    private AppUser transferredLogin;
    private Student amina;
    private Exam exam;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        smsProvider.clearOutbox();
        admin = appUserRepository.save(user("admin", Role.ADMIN, true));
        teacher = appUserRepository.save(user("t-1001", Role.TEACHER, true));
        disabledTeacher = appUserRepository.save(user("t-1002", Role.TEACHER, false));
        aminaLogin = appUserRepository.save(user("adm-001", Role.STUDENT, true));
        oldYearLogin = appUserRepository.save(user("adm-900", Role.STUDENT, true));
        transferredLogin = appUserRepository.save(user("adm-003", Role.STUDENT, true));

        AcademicYear old = academicYearRepository.save(
                new AcademicYear("2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);
        SchoolClass class9A = schoolClassRepository.save(new SchoolClass(year, "Class 9", "A", "101", 40));
        SchoolClass class8A = schoolClassRepository.save(new SchoolClass(old, "Class 8", "A", "201", 40));

        amina = student("ADM-001", "Amina", "01711000001", class9A, 1, StudentStatus.ACTIVE, aminaLogin);
        student("ADM-002", "Babul", "01711000002", class9A, 2, StudentStatus.ACTIVE, null);
        student("ADM-003", "Chandni", "01711000003", class9A, 3, StudentStatus.TRANSFERRED, transferredLogin);
        student("ADM-900", "Old", "01711000009", class8A, 1, StudentStatus.ACTIVE, oldYearLogin);

        exam = examRepository.save(new Exam(year, "Half Yearly", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10)));
    }

    // ---------------------------------------------------------------- notice

    @Test
    void notice_afterCommit_notifiesEnabledTeachersAndStudents_notAdmins() {
        publishAndCommit(new NoticePublishedEvent(1L, "Sports day", NoticeAudience.ALL));

        assertThat(recipients()).containsExactlyInAnyOrder(
                teacher.getId(), aminaLogin.getId(), oldYearLogin.getId(), transferredLogin.getId());
        assertThat(rows()).allSatisfy(row -> {
            assertThat(row).containsEntry("title", "New notice")
                    .containsEntry("message", "Sports day")
                    .containsEntry("link", "/notices");
            assertThat(row.get("read_at")).isNull();
        });
        assertThat(recipients()).doesNotContain(admin.getId(), disabledTeacher.getId());
    }

    @Test
    void notice_forTeachersOnly() {
        publishAndCommit(new NoticePublishedEvent(1L, "Staff meeting", NoticeAudience.TEACHERS));

        assertThat(recipients()).containsExactly(teacher.getId());
    }

    @Test
    void notice_rolledBack_notifiesNobody() {
        publishAndRollBack(new NoticePublishedEvent(1L, "Sports day", NoticeAudience.ALL));

        assertThat(rows()).isEmpty();
    }

    @Test
    void notice_createdThroughTheApi_notifiesAfterItsCommit() throws Exception {
        String token = login("admin");
        mockMvc.perform(post("/api/v1/notices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Exam routine", "body", "See attached", "audience", "STUDENTS", "pinned", false))))
                .andExpect(status().isCreated());

        assertThat(recipients()).containsExactlyInAnyOrder(
                aminaLogin.getId(), oldYearLogin.getId(), transferredLogin.getId());
    }

    // ---------------------------------------------------------------- results

    @Test
    void results_afterCommit_notifyActiveStudentsOfTheExamYear_andSmsTheirGuardians() {
        publishAndCommit(new ResultsPublishedEvent(exam.getId(), "Half Yearly"));

        // Babul has no login: no in-app row, but his guardian still gets the SMS.
        assertThat(recipients()).containsExactly(aminaLogin.getId());
        assertThat(rows().get(0))
                .containsEntry("title", "Results published")
                .containsEntry("message", "Results of Half Yearly are now available.")
                .containsEntry("link", "/my/results");
        // Only the two ACTIVE students of the exam's year; local numbers became 880... MSISDNs.
        assertThat(smsProvider.outbox()).containsExactly(
                new SentSms("8801711000001", "Results of Half Yearly for Amina are published.", "SCHOOL"),
                new SentSms("8801711000002", "Results of Half Yearly for Babul are published.", "SCHOOL"));
    }

    @Test
    void results_rolledBack_notifyNobody() {
        publishAndRollBack(new ResultsPublishedEvent(exam.getId(), "Half Yearly"));

        assertThat(rows()).isEmpty();
        assertThat(smsProvider.outbox()).isEmpty();
    }

    // ---------------------------------------------------------------- payment

    @Test
    void payment_afterCommit_notifiesTheStudent_andSmsTheGuardianTheSameText() {
        publishAndCommit(paymentEvent());

        String text = "Payment of BDT 400.00 received for invoice INV-202611-ABCDEF. Ref: CASH-1";
        assertThat(recipients()).containsExactly(aminaLogin.getId());
        assertThat(rows().get(0))
                .containsEntry("title", "Payment received")
                .containsEntry("message", text)
                .containsEntry("link", "/my/fees");
        assertThat(smsProvider.outbox()).containsExactly(new SentSms("8801711000001", text, "SCHOOL"));
    }

    @Test
    void payment_rolledBack_notifiesNobody() {
        publishAndRollBack(paymentEvent());

        assertThat(rows()).isEmpty();
        assertThat(smsProvider.outbox()).isEmpty();
    }

    @Test
    void failingListener_neverUndoesTheOriginalAction() {
        // A notice title becomes the notification's message (at most 500 characters), so 600 characters
        // make the listener's INSERT fail inside the listener's OWN transaction.
        String tooLong = "x".repeat(600);

        // The "original action": a row written in the publisher's transaction.
        transaction.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, created_at)"
                    + " VALUES (?, 'Original', 'Written by the action', now())", admin.getId());
            eventPublisher.publishEvent(new NoticePublishedEvent(1L, tooLong, NoticeAudience.ALL));
        });

        // The listener failed (logged), its rows were rolled back, the original row stayed committed.
        assertThat(rows()).extracting(row -> row.get("title")).containsExactly("Original");
    }

    // ---------------------------------------------------------------- helpers

    private PaymentReceivedEvent paymentEvent() {
        return new PaymentReceivedEvent(50L, 7L, amina.getId(), new BigDecimal("400.00"),
                "INV-202611-ABCDEF", "CASH-1");
    }

    private void publishAndCommit(Object event) {
        transaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private void publishAndRollBack(Object event) {
        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });
    }

    private List<Long> recipients() {
        return jdbcTemplate.queryForList("SELECT user_id FROM notifications ORDER BY user_id", Long.class);
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("SELECT user_id, title, message, link, read_at FROM notifications ORDER BY id");
    }

    private AppUser user(String username, Role role, boolean enabled) {
        AppUser user = new AppUser(username, username + "@school.edu", passwordEncoder.encode("Password#1"),
                username + " Name", role);
        user.setEnabled(enabled);
        return user;
    }

    private Student student(String admissionNo, String firstName, String guardianPhone, SchoolClass schoolClass,
                            int roll, StudentStatus status, AppUser login) {
        Student student = new Student(admissionNo);
        student.setFirstName(firstName);
        student.setLastName("Test");
        student.setGender(Gender.FEMALE);
        student.setDateOfBirth(LocalDate.of(2011, 5, 10));
        student.setGuardianName("Parent " + firstName);
        student.setGuardianPhone(guardianPhone);
        student.setGuardianRelation("Mother");
        student.setAdmissionDate(LocalDate.of(2026, 1, 5));
        student.setSchoolClass(schoolClass);
        student.setRollNo(roll);
        student.setStatus(status);
        student.setUser(login);
        return studentRepository.save(student);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "Password#1"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
