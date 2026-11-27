package com.school.fee.gateway;

import com.school.fee.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes all {@link PaymentGateway} beans by {@link PaymentMethod}.
 */
@Component
public class PaymentGatewayFactory {

    private final Map<PaymentMethod, PaymentGateway> gateways;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayBeans) {
        Map<PaymentMethod, PaymentGateway> byMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentGateway gateway : gatewayBeans) {
            byMethod.put(gateway.method(), gateway);
        }
        this.gateways = Collections.unmodifiableMap(byMethod);
    }

    public PaymentGateway get(PaymentMethod method) {
        PaymentGateway gateway = method == null ? null : gateways.get(method);
        if (gateway == null) {
            throw new IllegalArgumentException("Payment method " + method + " is not supported");
        }
        return gateway;
    }
}
