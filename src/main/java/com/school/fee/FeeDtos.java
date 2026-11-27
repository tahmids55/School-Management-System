package com.school.fee;

import com.school.academic.SchoolClass;
import com.school.common.SecurityUtils;
import com.school.student.Student;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

public final class FeeDtos {

    private FeeDtos() {
    }

    public record InvoiceRequest(
            @NotNull(message = "Student is required")
            Long studentId,

            @NotBlank(message = "Title is required")
            @Size(max = 120, message = "Title must be at most 120 characters")
            String title,

            @NotNull(message = "Amount is required")
            @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
            @DecimalMax(value = "1000000.00", message = "Amount must be at most 1000000.00")
            @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
            BigDecimal amount,

            @DecimalMin(value = "0.00", message = "Discount cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Discount must have at most 2 decimal places")
            BigDecimal discount,

            @NotNull(message = "Due date is required")
            LocalDate dueDate
    ) {
    }

    public record BulkInvoiceRequest(
            @NotNull(message = "Class is required")
            Long classId,

            @NotBlank(message = "Title is required")
            @Size(max = 120, message = "Title must be at most 120 characters")
            String title,

            @NotNull(message = "Amount is required")
            @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
            @DecimalMax(value = "1000000.00", message = "Amount must be at most 1000000.00")
            @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
            BigDecimal amount,

            @NotNull(message = "Due date is required")
            LocalDate dueDate
    ) {
    }

    public record BulkResult(int created, int skipped) {
    }

    public record InvoiceResponse(
            Long id,
            String invoiceNo,
            Long studentId,
            String studentName,
            String admissionNo,
            String className,
            String title,
            BigDecimal amount,
            BigDecimal discount,
            BigDecimal payable,
            BigDecimal amountPaid,
            BigDecimal outstanding,
            LocalDate dueDate,
            InvoiceStatus status,
            boolean overdue
    ) {
        public static InvoiceResponse from(FeeInvoice invoice, LocalDate today) {
            Student student = invoice.getStudent();
            SchoolClass schoolClass = student.getSchoolClass();
            return new InvoiceResponse(
                    invoice.getId(),
                    invoice.getInvoiceNo(),
                    student.getId(),
                    student.getFullName(),
                    student.getAdmissionNo(),
                    schoolClass != null ? schoolClass.getDisplayName() : null,
                    invoice.getTitle(),
                    invoice.getAmount(),
                    invoice.getDiscount(),
                    invoice.payable(),
                    invoice.getAmountPaid(),
                    invoice.outstanding(),
                    invoice.getDueDate(),
                    invoice.getStatus(),
                    invoice.isOverdue(today)
            );
        }
    }

    public record FeeSummary(
            BigDecimal totalBilled,
            BigDecimal totalCollected,
            BigDecimal totalOutstanding,
            long overdueInvoices,
            BigDecimal collectionRate
    ) {
        public static FeeSummary of(BigDecimal billedRaw, BigDecimal collectedRaw, long overdueInvoices) {
            BigDecimal totalBilled = scale2(billedRaw);
            BigDecimal totalCollected = scale2(collectedRaw);
            BigDecimal totalOutstanding = totalBilled.subtract(totalCollected).setScale(2, RoundingMode.HALF_UP);
            BigDecimal collectionRate = totalBilled.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                    : totalCollected.multiply(BigDecimal.valueOf(100))
                            .divide(totalBilled, 1, RoundingMode.HALF_UP);
            return new FeeSummary(totalBilled, totalCollected, totalOutstanding, overdueInvoices, collectionRate);
        }

        private static BigDecimal scale2(BigDecimal value) {
            return value == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                    : value.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public record PaymentRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
            BigDecimal amount,

            @NotNull(message = "Payment method is required")
            PaymentMethod method,

            @Size(max = 80, message = "Reference must be at most 80 characters")
            String reference
    ) {
    }

    public record OnlinePaymentRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
            BigDecimal amount
    ) {
    }

    public record PaymentResponse(
            Long id,
            Long invoiceId,
            String invoiceNo,
            BigDecimal amount,
            PaymentMethod method,
            String transactionRef,
            Instant paidAt,
            String receivedBy,
            InvoiceStatus invoiceStatus,
            BigDecimal invoiceOutstanding
    ) {
        public static PaymentResponse from(Payment payment) {
            FeeInvoice invoice = payment.getInvoice();
            String receivedBy = payment.getCreatedBy() != null
                    ? payment.getCreatedBy()
                    : SecurityUtils.currentUsernameOrSystem();
            return new PaymentResponse(
                    payment.getId(),
                    invoice.getId(),
                    invoice.getInvoiceNo(),
                    payment.getAmount(),
                    payment.getMethod(),
                    payment.getTransactionRef(),
                    payment.getPaidAt(),
                    receivedBy,
                    invoice.getStatus(),
                    invoice.outstanding()
            );
        }
    }
}
