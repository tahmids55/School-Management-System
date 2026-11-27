package com.school.fee.gateway;

import com.school.fee.PaymentMethod;
import org.springframework.stereotype.Component;

/**
 * Adapts the two-step {@link FakeBkashSdk} ({@code createPayment} + {@code executePayment})
 * into a single {@link PaymentGateway#charge(ChargeRequest)} call.
 */
@Component
public class BkashPaymentAdapter implements PaymentGateway {

    private final FakeBkashSdk bkashSdk;

    public BkashPaymentAdapter(FakeBkashSdk bkashSdk) {
        this.bkashSdk = bkashSdk;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.BKASH;
    }

    @Override
    public GatewayResult charge(ChargeRequest request) {
        String amountText = request.amount().toPlainString();
        String paymentId = bkashSdk.createPayment(amountText, request.invoiceNo());
        FakeBkashSdk.ExecuteResponse response = bkashSdk.executePayment(paymentId);
        if ("0000".equals(response.statusCode())) {
            return GatewayResult.ok(response.trxID());
        }
        String message = (response.statusMessage() != null && !response.statusMessage().isBlank())
                ? response.statusMessage()
                : "bKash payment declined";
        return GatewayResult.declined(message);
    }
}
