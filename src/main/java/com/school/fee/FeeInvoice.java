package com.school.fee;

import com.school.common.BaseEntity;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.student.Student;
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
import java.time.LocalDate;
import java.util.Objects;

/**
 * Fee invoice with encapsulated money calculations and state transitions.
 * State changes only through {@link #applyPayment(BigDecimal)}, {@link #checkPayable()}, and {@link #cancel()}.
 */
@Getter
@Entity
@Table(name = "fee_invoices")
public class FeeInvoice extends BaseEntity {

    @Column(name = "invoice_no", nullable = false, unique = true, length = 30, updatable = false)
    private String invoiceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    protected FeeInvoice() {
    }

    public FeeInvoice(
            String invoiceNo,
            Student student,
            String title,
            BigDecimal amount,
            BigDecimal discount,
            LocalDate dueDate
    ) {
        Objects.requireNonNull(invoiceNo, "invoiceNo is required");
        Objects.requireNonNull(student, "student is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(dueDate, "dueDate is required");

        BigDecimal normalizedAmount = scaleMoney(amount);
        BigDecimal normalizedDiscount = discount == null ? zeroMoney() : scaleMoney(discount);
        if (normalizedDiscount.compareTo(normalizedAmount) > 0) {
            throw new BusinessRuleException("Discount cannot be larger than the amount");
        }

        this.invoiceNo = invoiceNo;
        this.student = student;
        this.title = title;
        this.amount = normalizedAmount;
        this.discount = normalizedDiscount;
        this.amountPaid = zeroMoney();
        this.dueDate = dueDate;
        this.status = InvoiceStatus.UNPAID;
    }

    public BigDecimal payable() {
        return amount.subtract(discount);
    }

    public BigDecimal outstanding() {
        return payable().subtract(amountPaid);
    }

    /** Due on {@code today} itself is not overdue; only strictly before {@code today}. */
    public boolean isOverdue(LocalDate today) {
        return (status == InvoiceStatus.UNPAID || status == InvoiceStatus.PARTIALLY_PAID)
                && dueDate.isBefore(today);
    }

    public void checkPayable() {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.CANCELLED) {
            throw new ConflictException("Invoice " + invoiceNo + " is " + status.name() + " and cannot be paid");
        }
    }

    /** Validates payable state, positive amount, and remaining balance without mutating state. */
    public BigDecimal validatePayment(BigDecimal payment) {
        checkPayable();
        if (payment == null || payment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be positive");
        }
        BigDecimal normalizedPayment = scaleMoney(payment);
        BigDecimal remaining = outstanding();
        if (normalizedPayment.compareTo(remaining) > 0) {
            throw new BusinessRuleException(
                    "Payment of " + normalizedPayment.toPlainString()
                            + " exceeds the outstanding " + remaining.toPlainString()
            );
        }
        return normalizedPayment;
    }

    public void applyPayment(BigDecimal payment) {
        BigDecimal normalizedPayment = validatePayment(payment);
        this.amountPaid = this.amountPaid.add(normalizedPayment);
        this.status = outstanding().compareTo(BigDecimal.ZERO) == 0
                ? InvoiceStatus.PAID
                : InvoiceStatus.PARTIALLY_PAID;
    }

    public void cancel() {
        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Invoice " + invoiceNo + " already has payments and cannot be cancelled");
        }
        if (status == InvoiceStatus.CANCELLED) {
            throw new ConflictException("Invoice " + invoiceNo + " is already cancelled");
        }
        this.status = InvoiceStatus.CANCELLED;
    }

    private static BigDecimal scaleMoney(BigDecimal value) {
        return value.scale() <= 2 ? value.setScale(2, RoundingMode.UNNECESSARY) : value;
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }
}
