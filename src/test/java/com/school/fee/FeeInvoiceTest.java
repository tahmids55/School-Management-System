package com.school.fee;

import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.student.Student;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeeInvoiceTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 25);

    private FeeInvoice invoice(String amount, String discount, LocalDate dueDate) {
        Student student = new Student("ADM-2026-001");
        return new FeeInvoice(
                "INV-202611-ABC234",
                student,
                "Tuition Fee - November 2026",
                new BigDecimal(amount),
                discount == null ? null : new BigDecimal(discount),
                dueDate
        );
    }

    @Test
    void payable_subtractsDiscount() {
        FeeInvoice inv = invoice("2500.00", "250.50", DUE);

        assertThat(inv.payable()).isEqualByComparingTo("2249.50");
        assertThat(inv.outstanding()).isEqualByComparingTo("2249.50");
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
    }

    @Test
    void discountLargerThanAmount_isRejected() {
        assertThatThrownBy(() -> invoice("1000.00", "1000.01", DUE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Discount cannot be larger than the amount");
    }

    @Test
    void partialThenFullPayment_changesStatus() {
        FeeInvoice inv = invoice("1000.00", "200.00", DUE);

        inv.applyPayment(new BigDecimal("300.00"));
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(inv.getAmountPaid()).isEqualByComparingTo("300.00");
        assertThat(inv.outstanding()).isEqualByComparingTo("500.00");

        inv.applyPayment(new BigDecimal("500.00"));
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(inv.getAmountPaid()).isEqualByComparingTo("800.00");
        assertThat(inv.outstanding()).isEqualByComparingTo("0.00");
    }

    @Test
    void overpayment_isRejected_byOneCent() {
        FeeInvoice inv = invoice("1000.00", "200.00", DUE);
        inv.applyPayment(new BigDecimal("300.00"));

        assertThatThrownBy(() -> inv.applyPayment(new BigDecimal("500.01")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Payment of 500.01 exceeds the outstanding 500.00");
    }

    @Test
    void zeroOrNegativePayment_isRejected() {
        FeeInvoice inv = invoice("1000.00", "0.00", DUE);

        assertThatThrownBy(() -> inv.applyPayment(BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Payment amount must be positive");

        assertThatThrownBy(() -> inv.applyPayment(new BigDecimal("-10.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Payment amount must be positive");
    }

    @Test
    void paidInvoice_cannotBePaidAgain() {
        FeeInvoice inv = invoice("500.00", "0.00", DUE);
        inv.applyPayment(new BigDecimal("500.00"));

        assertThatThrownBy(inv::checkPayable)
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invoice INV-202611-ABC234 is PAID and cannot be paid");

        assertThatThrownBy(() -> inv.applyPayment(new BigDecimal("1.00")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invoice INV-202611-ABC234 is PAID and cannot be paid");
    }

    @Test
    void invoiceWithPayments_cannotBeCancelled() {
        FeeInvoice partial = invoice("500.00", "0.00", DUE);
        partial.applyPayment(new BigDecimal("100.00"));

        assertThatThrownBy(partial::cancel)
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invoice INV-202611-ABC234 already has payments and cannot be cancelled");

        FeeInvoice paid = invoice("500.00", "0.00", DUE);
        paid.applyPayment(new BigDecimal("500.00"));

        assertThatThrownBy(paid::cancel)
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invoice INV-202611-ABC234 already has payments and cannot be cancelled");
    }

    @Test
    void cancelledInvoice_cannotBePaid() {
        FeeInvoice inv = invoice("500.00", "0.00", DUE);
        inv.cancel();

        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThatThrownBy(() -> inv.applyPayment(new BigDecimal("100.00")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invoice INV-202611-ABC234 is CANCELLED and cannot be paid");
    }

    @Test
    void overdue_onlyAfterDueDateAndWhileUnpaid() {
        FeeInvoice unpaid = invoice("500.00", "0.00", DUE);
        assertThat(unpaid.isOverdue(DUE.minusDays(1))).isFalse();
        assertThat(unpaid.isOverdue(DUE)).isFalse();
        assertThat(unpaid.isOverdue(DUE.plusDays(1))).isTrue();

        unpaid.applyPayment(new BigDecimal("100.00"));
        assertThat(unpaid.isOverdue(DUE.plusDays(1))).isTrue();

        unpaid.applyPayment(new BigDecimal("400.00"));
        assertThat(unpaid.isOverdue(DUE.plusDays(1))).isFalse();

        FeeInvoice cancelled = invoice("500.00", "0.00", DUE);
        cancelled.cancel();
        assertThat(cancelled.isOverdue(DUE.plusDays(1))).isFalse();
    }
}
