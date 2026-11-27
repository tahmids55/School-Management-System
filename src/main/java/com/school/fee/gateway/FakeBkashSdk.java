package com.school.fee.gateway;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated external bKash SDK (intentionally not a Spring component; wired via {@link GatewayConfig}).
 */
public class FakeBkashSdk {

    private static final BigDecimal TRANSACTION_LIMIT = new BigDecimal("50000");
    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, String> pendingAmounts = new ConcurrentHashMap<>();

    public String createPayment(String amount, String invoiceNo) {
        String paymentId = "TR00" + randomChars(12);
        pendingAmounts.put(paymentId, amount == null ? "" : amount.trim());
        return paymentId;
    }

    public ExecuteResponse executePayment(String paymentId) {
        if (paymentId == null) {
            return new ExecuteResponse("2056", null, "Invalid payment ID");
        }
        String rawAmount = pendingAmounts.remove(paymentId);
        if (rawAmount == null) {
            return new ExecuteResponse("2056", null, "Invalid payment ID");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(rawAmount);
        } catch (NumberFormatException ex) {
            return new ExecuteResponse("2056", null, "Invalid payment ID");
        }

        if (amount.compareTo(TRANSACTION_LIMIT) > 0) {
            return new ExecuteResponse("2023", null, "Transaction limit exceeded");
        }
        return new ExecuteResponse("0000", "BK" + randomChars(10), "Successful");
    }

    private static String randomChars(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    public record ExecuteResponse(
            String statusCode,
            String trxID,
            String statusMessage
    ) {
    }
}
