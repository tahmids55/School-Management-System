package com.school.fee;

import com.school.CommittedIntegrationTestBase;
import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.audit.AuditLogRepository;
import com.school.fee.gateway.CashPaymentGateway;
import com.school.fee.gateway.PaymentGateway;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(PaymentCommittedIntegrationTest.CountingGatewayConfig.class)
class PaymentCommittedIntegrationTest extends CommittedIntegrationTestBase {

    @TestConfiguration
    static class CountingGatewayConfig {

        @Bean
        @Primary
        CountingCashGateway countingCashGateway(Clock clock) {
            return new CountingCashGateway(clock);
        }

        @Bean
        PaymentEventRecorder paymentEventRecorder() {
            return new PaymentEventRecorder();
        }
    }

    static class CountingCashGateway implements PaymentGateway {

        private final CashPaymentGateway delegate;
        private final AtomicInteger calls = new AtomicInteger();

        CountingCashGateway(Clock clock) {
            this.delegate = new CashPaymentGateway(clock);
        }

        @Override
        public PaymentMethod method() {
            return PaymentMethod.CASH;
        }

        @Override
        public GatewayResult charge(ChargeRequest request) {
            calls.incrementAndGet();
            return delegate.charge(request);
        }

        int count() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }

    static class PaymentEventRecorder {
        private final List<PaymentReceivedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onPayment(PaymentReceivedEvent event) {
            events.add(event);
        }

        List<PaymentReceivedEvent> events() {
            return events;
        }

        void clear() {
            events.clear();
        }
    }

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
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
    private CountingCashGateway countingCashGateway;
    @Autowired
    private PaymentEventRecorder eventRecorder;

    private FeeInvoice invoice;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        countingCashGateway.reset();
        eventRecorder.clear();

        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN));
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);
        SchoolClass schoolClass = schoolClassRepository.save(
                new SchoolClass(year, "Class 9", "A", "101", 40, null));

        Student student = new Student("ADM-2026-001");
        student.setFirstName("Amina");
        student.setLastName("Islam");
        student.setGender(Gender.FEMALE);
        student.setDateOfBirth(LocalDate.of(2012, 5, 10));
        student.setGuardianName("Parent Amina");
        student.setGuardianPhone("01700000001");
        student.setGuardianRelation("Mother");
        student.setSchoolClass(schoolClass);
        student.setRollNo(1);
        student.setStatus(StudentStatus.ACTIVE);
        student.setAdmissionDate(LocalDate.of(2026, 1, 5));
        student = studentRepository.save(student);

        invoice = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-CON001",
                student,
                "Annual Fee",
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                LocalDate.of(2026, 12, 15)
        ));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                                "password", "AdminPass#1"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void eightParallelRequestsWithSameKey_chargeOnce_persistOneRow_andReturnEightEqualAnswers() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "amount", "400.00",
                "method", "CASH"
        ));

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<MvcResult>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", invoice.getId())
                                .header("Authorization", "Bearer " + adminToken)
                                .header("Idempotency-Key", "parallel-same-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
            });
        }

        List<Future<MvcResult>> futures = new ArrayList<>();
        for (Callable<MvcResult> task : tasks) {
            futures.add(executor.submit(task));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(results)
                .hasSize(threads)
                .allSatisfy(res -> assertThat(res.getResponse().getStatus()).isEqualTo(201));

        String firstBody = results.get(0).getResponse().getContentAsString();
        assertThat(results)
                .extracting(res -> {
                    try {
                        return res.getResponse().getContentAsString();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .containsOnly(firstBody);

        assertThat(countingCashGateway.count()).isEqualTo(1);
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(eventRecorder.events()).hasSize(1);

        FeeInvoice updated = feeInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updated.getAmountPaid()).isEqualByComparingTo("400.00");
        assertThat(updated.outstanding()).isEqualByComparingTo("600.00");
        assertThat(updated.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void eightParallelRequestsWithDifferentKeys_neverChargeMoreThanOutstanding() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "amount", "300.00",
                "method", "CASH"
        ));

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<MvcResult>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", invoice.getId())
                                .header("Authorization", "Bearer " + adminToken)
                                .header("Idempotency-Key", "parallel-diff-key-" + index)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
            });
        }

        List<Future<MvcResult>> futures = new ArrayList<>();
        for (Callable<MvcResult> task : tasks) {
            futures.add(executor.submit(task));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS).getResponse().getStatus());
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        long createdCount = statuses.stream().filter(s -> s == 201).count();
        long rejectedCount = statuses.stream().filter(s -> s == 422).count();
        assertThat(createdCount).isEqualTo(3);
        assertThat(rejectedCount).isEqualTo(5);
        assertThat(countingCashGateway.count()).isEqualTo(3);
        assertThat(paymentRepository.count()).isEqualTo(3);

        FeeInvoice updated = feeInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updated.getAmountPaid()).isEqualByComparingTo("900.00");
        assertThat(updated.outstanding()).isEqualByComparingTo("100.00");
        assertThat(updated.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void declinedBkashPayment_returns402_leavesInvoiceUnchanged_andKeepsAuditRow() throws Exception {
        FeeInvoice largeInvoice = feeInvoiceRepository.save(new FeeInvoice(
                "INV-202611-BIG001",
                feeInvoiceRepository.findByIdWithStudentAndClass(invoice.getId()).orElseThrow().getStudent(),
                "Annual Tuition",
                new BigDecimal("60000.00"),
                BigDecimal.ZERO,
                LocalDate.of(2026, 12, 15)
        ));

        mockMvc.perform(post("/api/v1/fees/invoices/{id}/payments", largeInvoice.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "bkash-limit-exceeded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", "50000.01",
                                "method", "BKASH"
                        ))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.detail").value("Transaction limit exceeded"));

        FeeInvoice unchanged = feeInvoiceRepository.findById(largeInvoice.getId()).orElseThrow();
        assertThat(unchanged.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(unchanged.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(paymentRepository.count()).isZero();
        assertThat(eventRecorder.events()).isEmpty();
        assertThat(auditLogRepository.findAll())
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo(PaymentService.AUDIT_PAYMENT_DECLINED);
                    assertThat(log.getEntityId()).isEqualTo(String.valueOf(largeInvoice.getId()));
                    assertThat(log.getDetails()).contains("Transaction limit exceeded");
                });
    }
}
