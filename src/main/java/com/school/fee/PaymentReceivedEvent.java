package com.school.fee;

import java.math.BigDecimal;

/**
 * Domain event published after a payment is applied and committed (downstream listeners such as F26 subscribe).
 */
public record PaymentReceivedEvent(
        Long paymentId,
        Long invoiceId,
        Long studentId,
        BigDecimal amount,
        String invoiceNo,
        String transactionRef
) {
}
