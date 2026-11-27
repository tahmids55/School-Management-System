package com.school.fee.gateway;

import com.school.common.exception.BusinessRuleException;
import com.school.fee.PaymentMethod;
import org.springframework.stereotype.Component;

/**
 * Bank transfer payments require the payer's bank reference and record it as the transaction reference.
 */
@Component
public class BankTransferPaymentGateway implements PaymentGateway {

    private static final int MAX_REFERENCE_LENGTH = 80;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public GatewayResult charge(ChargeRequest request) {
        String raw = request == null ? null : request.payerReference();
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("Bank transaction reference is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_REFERENCE_LENGTH) {
            throw new BusinessRuleException("Bank transaction reference is too long (max 80)");
        }
        return GatewayResult.ok(trimmed);
    }
}
