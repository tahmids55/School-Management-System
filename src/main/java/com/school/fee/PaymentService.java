package com.school.fee;

import com.school.audit.AuditService;
import com.school.common.SecurityUtils;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.ForbiddenException;
import com.school.common.exception.NotFoundException;
import com.school.common.exception.PaymentFailedException;
import com.school.fee.FeeDtos.PaymentRequest;
import com.school.fee.FeeDtos.PaymentResponse;
import com.school.fee.gateway.PaymentGateway;
import com.school.fee.gateway.PaymentGateway.ChargeRequest;
import com.school.fee.gateway.PaymentGateway.GatewayResult;
import com.school.fee.gateway.PaymentGatewayFactory;
import com.school.security.StudentAccessGuard;
import com.school.student.Student;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Idempotent payment recording and payment history queries.
 * Serializes concurrent attempts sharing an idempotency key with a PostgreSQL advisory transaction lock
 * before locking the invoice row and querying the key.
 */
@Service
public class PaymentService {

    public static final String ENTITY_TYPE = "Payment";
    public static final String AUDIT_PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String AUDIT_PAYMENT_DECLINED = "PAYMENT_DECLINED";

    public static final String MISSING_KEY_MESSAGE = "The Idempotency-Key header is required for payments";
    public static final String KEY_TOO_LONG_MESSAGE = "Idempotency-Key is too long (max 80)";
    public static final String KEY_REUSED_DIFFERENT_INVOICE_MESSAGE =
            "This Idempotency-Key was already used for a different invoice";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 80;

    private final PaymentRepository paymentRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final StudentAccessGuard studentAccessGuard;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            FeeInvoiceRepository feeInvoiceRepository,
            PaymentGatewayFactory gatewayFactory,
            StudentAccessGuard studentAccessGuard,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.gatewayFactory = gatewayFactory;
        this.studentAccessGuard = studentAccessGuard;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForInvoice(Long invoiceId) {
        if (SecurityUtils.hasRole("ADMIN")) {
            if (!feeInvoiceRepository.existsById(invoiceId)) {
                throw new NotFoundException("Invoice", invoiceId);
            }
        } else if (SecurityUtils.hasRole("STUDENT")) {
            Student current = studentAccessGuard.currentStudent();
            FeeInvoice invoice = feeInvoiceRepository.findByIdWithStudentAndClass(invoiceId)
                    .orElseThrow(() -> new ForbiddenException(StudentAccessGuard.OWN_RECORDS_ONLY));
            if (!invoice.getStudent().getId().equals(current.getId())) {
                throw new ForbiddenException(StudentAccessGuard.OWN_RECORDS_ONLY);
            }
        } else {
            throw new ForbiddenException("You do not have permission to view payment records");
        }

        return paymentRepository.findAllByInvoiceIdWithInvoice(invoiceId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /**
     * Records a payment idempotently. Declines throw {@link PaymentFailedException} (HTTP 402)
     * with {@code noRollbackFor} so the {@code PAYMENT_DECLINED} audit entry is persisted while the invoice stays unchanged.
     */
    @Transactional(noRollbackFor = PaymentFailedException.class)
    public PaymentResponse recordPayment(Long invoiceId, PaymentRequest request, String rawIdempotencyKey) {
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);

        // Lock order: advisory xact lock on key hash -> invoice row FOR UPDATE -> idempotency key lookup.
        paymentRepository.acquireAdvisoryLock(hashKey(idempotencyKey));
        FeeInvoice invoice = feeInvoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice", invoiceId));

        Optional<Payment> existing = paymentRepository.findByIdempotencyKeyWithInvoice(idempotencyKey);
        if (existing.isPresent()) {
            Payment found = existing.get();
            if (!found.getInvoice().getId().equals(invoiceId)) {
                throw new ConflictException(KEY_REUSED_DIFFERENT_INVOICE_MESSAGE);
            }
            return PaymentResponse.from(found);
        }

        PaymentGateway gateway = gatewayFactory.get(request.method());
        BigDecimal scaledAmount = invoice.validatePayment(request.amount());

        String trimmedRef = normalizeOptional(request.reference());
        String payerReference = request.method() == PaymentMethod.BANK_TRANSFER ? trimmedRef : idempotencyKey;

        GatewayResult result = gateway.charge(new ChargeRequest(invoice.getInvoiceNo(), scaledAmount, payerReference));
        if (result == null || !result.success()) {
            String message = (result != null && result.message() != null && !result.message().isBlank())
                    ? result.message()
                    : "Payment declined";
            auditService.record(
                    AUDIT_PAYMENT_DECLINED,
                    FeeInvoiceService.ENTITY_TYPE,
                    String.valueOf(invoice.getId()),
                    "Declined " + request.method() + " payment of " + scaledAmount.toPlainString()
                            + " BDT for " + invoice.getInvoiceNo() + ": " + message
            );
            throw new PaymentFailedException(message);
        }

        invoice.applyPayment(scaledAmount);
        feeInvoiceRepository.saveAndFlush(invoice);

        // PostgreSQL TIMESTAMPTZ stores microseconds, so truncate here to keep creator and replay responses identical.
        Instant paidAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Payment payment = new Payment(
                invoice,
                scaledAmount,
                request.method(),
                result.transactionRef(),
                idempotencyKey,
                paidAt
        );
        Payment saved = paymentRepository.saveAndFlush(payment);

        auditService.record(
                AUDIT_PAYMENT_RECEIVED,
                ENTITY_TYPE,
                String.valueOf(saved.getId()),
                "Recorded " + saved.getMethod() + " payment of " + saved.getAmount().toPlainString()
                        + " BDT for " + invoice.getInvoiceNo() + " (ref " + saved.getTransactionRef() + ")"
        );
        eventPublisher.publishEvent(new PaymentReceivedEvent(
                saved.getId(),
                invoice.getId(),
                invoice.getStudent().getId(),
                saved.getAmount(),
                invoice.getInvoiceNo(),
                saved.getTransactionRef()
        ));

        return PaymentResponse.from(saved);
    }

    private static String normalizeIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new BusinessRuleException(MISSING_KEY_MESSAGE);
        }
        String trimmed = rawKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessRuleException(KEY_TOO_LONG_MESSAGE);
        }
        return trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static long hashKey(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
