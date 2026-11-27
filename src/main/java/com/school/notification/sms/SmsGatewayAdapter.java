package com.school.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter pattern: makes the provider's {@code submitMessage(msisdn, text, senderId) -> status code}
 * look like our simple {@link SmsSender#send(String, String)}.
 * It converts the number, adds the sender id, turns the status code into true/false,
 * and swallows every error so callers never see an exception.
 */
public class SmsGatewayAdapter implements SmsSender {

    static final String SENDER_ID = "SCHOOL";

    private static final Logger log = LoggerFactory.getLogger(SmsGatewayAdapter.class);

    private final FakeSmsGatewayClient client;
    /** False in production: message texts can hold personal data, so they are never logged there. */
    private final boolean logText;

    public SmsGatewayAdapter(FakeSmsGatewayClient client, boolean logText) {
        this.client = client;
        this.logText = logText;
    }

    @Override
    public boolean send(String phoneNumber, String text) {
        String msisdn = toMsisdn(phoneNumber);
        if (msisdn.isEmpty()) {
            log.info("SMS skipped: no phone number");
            return false;
        }
        try {
            int status = client.submitMessage(msisdn, text, SENDER_ID);
            boolean accepted = status == FakeSmsGatewayClient.ACCEPTED;
            if (accepted) {
                log.info("SMS accepted for {}{}", masked(msisdn), logText ? ": " + text : "");
            } else {
                log.warn("SMS rejected for {} with status {}", masked(msisdn), status);
            }
            return accepted;
        } catch (RuntimeException ex) {
            log.warn("SMS provider failed for {}: {}", masked(msisdn), ex.getClass().getSimpleName());
            return false;
        }
    }

    /** "01711-123456" -> "8801711123456": digits only, and a local number (leading 0) gets 88 in front. */
    static String toMsisdn(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        String digits = phoneNumber.replaceAll("\\D", "");
        return digits.startsWith("0") ? "88" + digits : digits;
    }

    /** Only the last 3 digits ever reach the log: "***456". */
    static String masked(String msisdn) {
        return "***" + msisdn.substring(Math.max(0, msisdn.length() - 3));
    }
}
