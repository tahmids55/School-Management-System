package com.school.fee;

import com.school.common.BaseEntity;
import com.school.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Recorded payment against a {@link FeeInvoice}, deduplicated by {@code idempotencyKey}.
 */
@Getter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private FeeInvoice invoice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "transaction_ref", nullable = false, length = 80)
    private String transactionRef;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 80, updatable = false)
    private String idempotencyKey;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    protected Payment() {
    }

    public Payment(
            FeeInvoice invoice,
            BigDecimal amount,
            PaymentMethod method,
            String transactionRef,
            String idempotencyKey,
            Instant paidAt
    ) {
        Objects.requireNonNull(invoice, "invoice is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(method, "method is required");
        Objects.requireNonNull(transactionRef, "transactionRef is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(paidAt, "paidAt is required");

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be positive");
        }

        this.invoice = invoice;
        this.amount = amount.scale() <= 2 ? amount.setScale(2, RoundingMode.UNNECESSARY) : amount;
        this.method = method;
        this.transactionRef = transactionRef;
        this.idempotencyKey = idempotencyKey;
        this.paidAt = paidAt;
    }
}
