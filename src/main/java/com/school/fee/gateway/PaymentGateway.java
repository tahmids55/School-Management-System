package com.school.fee.gateway;

import com.school.fee.PaymentMethod;

import java.math.BigDecimal;

/**
 * Interchangeable payment gateway contract for charging an invoice.
 */
public interface PaymentGateway {

    PaymentMethod method();

    GatewayResult charge(ChargeRequest request);

    record ChargeRequest(
            String invoiceNo,
            BigDecimal amount,
            String payerReference
    ) {
    }

    record GatewayResult(
            boolean success,
            String transactionRef,
            String message
    ) {
        public static GatewayResult ok(String transactionRef) {
            return new GatewayResult(true, transactionRef, null);
        }

        public static GatewayResult declined(String message) {
            return new GatewayResult(false, null, message);
        }
    }
}
