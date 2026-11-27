package com.school.fee.gateway;

import com.school.fee.PaymentMethod;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Cash payments always succeed locally and generate a {@code CASH-yyyyMMdd-XXXXXX} reference.
 */
@Component
public class CashPaymentGateway implements PaymentGateway {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;

    public CashPaymentGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CASH;
    }

    @Override
    public GatewayResult charge(ChargeRequest request) {
        String datePart = DATE_FORMAT.format(LocalDate.now(clock));
        String digits = String.format("%06d", RANDOM.nextInt(1_000_000));
        return GatewayResult.ok("CASH-" + datePart + "-" + digits);
    }
}
