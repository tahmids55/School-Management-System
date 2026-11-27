package com.school.fee;

import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.ForbiddenException;
import com.school.common.exception.NotFoundException;
import com.school.common.exception.PaymentFailedException;
import com.school.fee.FeeDtos.OnlinePaymentRequest;
import com.school.fee.FeeDtos.PaymentRequest;
import com.school.fee.FeeDtos.PaymentResponse;
import com.school.fee.gateway.PaymentGateway;
import com.school.fee.gateway.PaymentGateway.ChargeRequest;
import com.school.fee.gateway.PaymentGateway.GatewayResult;
import com.school.fee.gateway.PaymentGatewayFactory;
import com.school.security.StudentAccessGuard;
import com.school.student.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T06:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Dhaka"));
    private static final LocalDate DUE = LocalDate.of(2026, 12, 1);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private FeeInvoiceRepository feeInvoiceRepository;
    @Mock
    private PaymentGatewayFactory gatewayFactory;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                paymentRepository,
                feeInvoiceRepository,
                gatewayFactory,
                studentAccessGuard,
                auditService,
                eventPublisher,
                FIXED_CLOCK
        );
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username,
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
    }

    private FeeInvoice sampleInvoice(Long invoiceId, Long studentId, String amount) {
        Student student = new Student("ADM-2026-001");
        ReflectionTestUtils.setField(student, "id", studentId);
        FeeInvoice invoice = new FeeInvoice(
                "INV-202611-ABC123",
                student,
                "Tuition Fee",
                new BigDecimal(amount),
                BigDecimal.ZERO,
                DUE
        );
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        return invoice;
    }

    @Test
    void successfulPayment_updatesInvoiceAndPublishesEvent() {
        authenticate("admin", "ADMIN");
        FeeInvoice invoice = sampleInvoice(10L, 42L, "1000.00");
        when(feeInvoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdempotencyKeyWithInvoice("key-1")).thenReturn(Optional.empty());
        when(gatewayFactory.get(PaymentMethod.BKASH)).thenReturn(paymentGateway);
        when(paymentGateway.charge(new ChargeRequest("INV-202611-ABC123", new BigDecimal("600.00"), "key-1")))
                .thenReturn(GatewayResult.ok("BK1234567890"));
        when(feeInvoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 88L);
            ReflectionTestUtils.setField(saved, "createdBy", "admin");
            return saved;
        });

        PaymentResponse response = service.recordPayment(
                10L,
                new PaymentRequest(new BigDecimal("600.00"), PaymentMethod.BKASH, null),
                "  key-1  "
        );

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.invoiceId()).isEqualTo(10L);
        assertThat(response.invoiceNo()).isEqualTo("INV-202611-ABC123");
        assertThat(response.amount()).isEqualByComparingTo("600.00");
        assertThat(response.method()).isEqualTo(PaymentMethod.BKASH);
        assertThat(response.transactionRef()).isEqualTo("BK1234567890");
        assertThat(response.paidAt()).isEqualTo(NOW);
        assertThat(response.receivedBy()).isEqualTo("admin");
        assertThat(response.invoiceStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(response.invoiceOutstanding()).isEqualByComparingTo("400.00");

        verify(paymentRepository).acquireAdvisoryLock(anyLong());
        verify(feeInvoiceRepository).findByIdForUpdate(10L);
        verify(auditService).record(
                eq(PaymentService.AUDIT_PAYMENT_RECEIVED),
                eq(PaymentService.ENTITY_TYPE),
                eq("88"),
                any()
        );

        ArgumentCaptor<PaymentReceivedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentReceivedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PaymentReceivedEvent event = eventCaptor.getValue();
        assertThat(event.paymentId()).isEqualTo(88L);
        assertThat(event.invoiceId()).isEqualTo(10L);
        assertThat(event.studentId()).isEqualTo(42L);
        assertThat(event.amount()).isEqualByComparingTo("600.00");
        assertThat(event.invoiceNo()).isEqualTo("INV-202611-ABC123");
        assertThat(event.transactionRef()).isEqualTo("BK1234567890");
    }

    @Test
    void sameIdempotencyKey_returnsFirstPayment_withoutChargingAgain() {
        FeeInvoice invoice = sampleInvoice(10L, 42L, "1000.00");
        invoice.applyPayment(new BigDecimal("1000.00"));
        Payment existing = new Payment(
                invoice,
                new BigDecimal("1000.00"),
                PaymentMethod.CASH,
                "CASH-20261127-123456",
                "idem-replay",
                NOW
        );
        ReflectionTestUtils.setField(existing, "id", 55L);
        ReflectionTestUtils.setField(existing, "createdBy", "admin");

        when(feeInvoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdempotencyKeyWithInvoice("idem-replay")).thenReturn(Optional.of(existing));

        PaymentResponse replay = service.recordPayment(
                10L,
                new PaymentRequest(new BigDecimal("1000.00"), PaymentMethod.CASH, null),
                "idem-replay"
        );

        assertThat(replay.id()).isEqualTo(55L);
        assertThat(replay.transactionRef()).isEqualTo("CASH-20261127-123456");
        assertThat(replay.invoiceStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(replay.invoiceOutstanding()).isEqualByComparingTo("0.00");
        verifyNoInteractions(gatewayFactory, paymentGateway, auditService, eventPublisher);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void idempotencyKeyReusedForAnotherInvoice_isConflict() {
        FeeInvoice firstInvoice = sampleInvoice(10L, 42L, "1000.00");
        FeeInvoice secondInvoice = sampleInvoice(20L, 42L, "500.00");
        Payment existing = new Payment(
                firstInvoice,
                new BigDecimal("1000.00"),
                PaymentMethod.CASH,
                "CASH-20261127-111111",
                "shared-key",
                NOW
        );

        when(feeInvoiceRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(secondInvoice));
        when(paymentRepository.findByIdempotencyKeyWithInvoice("shared-key")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.recordPayment(
                20L,
                new PaymentRequest(new BigDecimal("500.00"), PaymentMethod.CASH, null),
                "shared-key"
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This Idempotency-Key was already used for a different invoice");

        verifyNoInteractions(gatewayFactory, paymentGateway, eventPublisher);
    }

    @Test
    void declinedByGateway_leavesInvoiceUnchanged() {
        FeeInvoice invoice = sampleInvoice(10L, 42L, "60000.00");
        when(feeInvoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdempotencyKeyWithInvoice("key-decline")).thenReturn(Optional.empty());
        when(gatewayFactory.get(PaymentMethod.BKASH)).thenReturn(paymentGateway);
        when(paymentGateway.charge(any())).thenReturn(GatewayResult.declined("Transaction limit exceeded"));

        assertThatThrownBy(() -> service.recordPayment(
                10L,
                new PaymentRequest(new BigDecimal("60000.00"), PaymentMethod.BKASH, null),
                "key-decline"
        ))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessage("Transaction limit exceeded");

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        verify(feeInvoiceRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(auditService).record(
                eq(PaymentService.AUDIT_PAYMENT_DECLINED),
                eq(FeeInvoiceService.ENTITY_TYPE),
                eq("10"),
                any()
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void missingIdempotencyKey_isRejected() {
        assertThatThrownBy(() -> service.recordPayment(
                10L,
                new PaymentRequest(new BigDecimal("100.00"), PaymentMethod.CASH, null),
                null
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The Idempotency-Key header is required for payments");

        assertThatThrownBy(() -> service.recordPayment(
                10L,
                new PaymentRequest(new BigDecimal("100.00"), PaymentMethod.CASH, null),
                "   "
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The Idempotency-Key header is required for payments");

        assertThatThrownBy(() -> service.recordPayment(
                10L,
                new PaymentRequest(new BigDecimal("100.00"), PaymentMethod.CASH, null),
                "x".repeat(81)
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Idempotency-Key is too long (max 80)");

        verifyNoInteractions(paymentRepository, feeInvoiceRepository, gatewayFactory, eventPublisher);
    }

    @Test
    void overpayment_isRejectedBeforeCallingTheGateway() {
        FeeInvoice invoice = sampleInvoice(10L, 42L, "500.00");
        invoice.applyPayment(new BigDecimal("200.00"));
        when(feeInvoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdempotencyKeyWithInvoice("key-overpay")).thenReturn(Optional.empty());
        when(gatewayFactory.get(PaymentMethod.CASH)).thenReturn(paymentGateway);

        assertThatThrownBy(() -> service.recordPayment(
                10L,
                new PaymentRequest(new BigDecimal("300.01"), PaymentMethod.CASH, null),
                "key-overpay"
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Payment of 300.01 exceeds the outstanding 300.00");

        verify(paymentGateway, never()).charge(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService, eventPublisher);
    }

    @Test
    void listForInvoice_restrictsToAdminOrOwningStudent() {
        authenticate("teacher1", "TEACHER");
        assertThatThrownBy(() -> service.listForInvoice(10L)).isInstanceOf(ForbiddenException.class);

        FeeInvoice aminaInvoice = sampleInvoice(10L, 42L, "500.00");
        Student babul = new Student("ADM-2026-002");
        ReflectionTestUtils.setField(babul, "id", 99L);

        authenticate("adm-2026-002", "STUDENT");
        when(studentAccessGuard.currentStudent()).thenReturn(babul);
        when(feeInvoiceRepository.findByIdWithStudentAndClass(10L)).thenReturn(Optional.of(aminaInvoice));
        assertThatThrownBy(() -> service.listForInvoice(10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(StudentAccessGuard.OWN_RECORDS_ONLY);

        authenticate("admin", "ADMIN");
        when(feeInvoiceRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.listForInvoice(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Invoice 999 was not found");
    }

    @Test
    void recordSelfPayment_forcesBkashAndPaysOwnInvoice() {
        authenticate("adm-2026-001", "STUDENT");
        Student amina = new Student("ADM-2026-001");
        ReflectionTestUtils.setField(amina, "id", 42L);
        FeeInvoice invoice = sampleInvoice(10L, 42L, "1000.00");

        when(studentAccessGuard.currentStudent()).thenReturn(amina);
        when(feeInvoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdempotencyKeyWithInvoice("self-key-1")).thenReturn(Optional.empty());
        when(gatewayFactory.get(PaymentMethod.BKASH)).thenReturn(paymentGateway);
        when(paymentGateway.charge(new ChargeRequest("INV-202611-ABC123", new BigDecimal("500.00"), "self-key-1")))
                .thenReturn(GatewayResult.ok("BK9876543210"));
        when(feeInvoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 91L);
            ReflectionTestUtils.setField(saved, "createdBy", "adm-2026-001");
            return saved;
        });

        PaymentResponse response = service.recordSelfPayment(
                10L,
                new OnlinePaymentRequest(new BigDecimal("500.00")),
                "self-key-1"
        );

        assertThat(response.id()).isEqualTo(91L);
        assertThat(response.method()).isEqualTo(PaymentMethod.BKASH);
        assertThat(response.transactionRef()).isEqualTo("BK9876543210");
        assertThat(response.receivedBy()).isEqualTo("adm-2026-001");
        assertThat(response.invoiceStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(response.invoiceOutstanding()).isEqualByComparingTo("500.00");
        verify(gatewayFactory).get(PaymentMethod.BKASH);
        verify(eventPublisher).publishEvent(any(PaymentReceivedEvent.class));
    }

    @Test
    void recordSelfPayment_rejectsOtherStudentsOrMissingInvoice() {
        Student babul = new Student("ADM-2026-002");
        ReflectionTestUtils.setField(babul, "id", 99L);
        FeeInvoice aminaInvoice = sampleInvoice(10L, 42L, "1000.00");

        when(studentAccessGuard.currentStudent()).thenReturn(babul);
        when(feeInvoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(aminaInvoice));
        when(feeInvoiceRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordSelfPayment(
                10L,
                new OnlinePaymentRequest(new BigDecimal("100.00")),
                "self-other-1"
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only pay your own invoices");

        assertThatThrownBy(() -> service.recordSelfPayment(
                999L,
                new OnlinePaymentRequest(new BigDecimal("100.00")),
                "self-missing-1"
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only pay your own invoices");

        verifyNoInteractions(gatewayFactory, paymentGateway, auditService, eventPublisher);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void recordSelfPayment_propagatesNoProfileForbidden() {
        when(studentAccessGuard.currentStudent()).thenThrow(new ForbiddenException(StudentAccessGuard.NO_PROFILE));

        assertThatThrownBy(() -> service.recordSelfPayment(
                10L,
                new OnlinePaymentRequest(new BigDecimal("100.00")),
                "self-noprofile"
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(StudentAccessGuard.NO_PROFILE);

        verifyNoInteractions(paymentRepository, feeInvoiceRepository, gatewayFactory, auditService, eventPublisher);
    }
}
