package com.school.notification.sms;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulated external SMS provider SDK with its OWN interface: international number (MSISDN),
 * a sender id, and an HTTP-like status code as the answer. Nothing is really sent; the last
 * accepted messages are kept in a small in-memory outbox so a demo or test can look at them.
 * Intentionally not a Spring component; wired by {@link SmsConfig}, like the fake bKash SDK.
 */
public class FakeSmsGatewayClient {

    public static final int ACCEPTED = 202;
    public static final int BAD_REQUEST = 400;
    private static final int OUTBOX_SIZE = 100;

    public record SentSms(String msisdn, String text, String senderId) {
    }

    private final List<SentSms> outbox = new ArrayList<>();

    /** Accepts Bangladeshi mobile numbers in international form: 880 1x xxxxxxxx (13 digits). */
    public synchronized int submitMessage(String msisdn, String text, String senderId) {
        if (msisdn == null || !msisdn.matches("8801\\d{9}")) {
            return BAD_REQUEST;
        }
        if (text == null || text.isBlank() || senderId == null || senderId.isBlank()) {
            return BAD_REQUEST;
        }
        if (outbox.size() == OUTBOX_SIZE) {
            outbox.remove(0);
        }
        outbox.add(new SentSms(msisdn, text, senderId));
        return ACCEPTED;
    }

    /** A copy of the accepted messages, oldest first. */
    public synchronized List<SentSms> outbox() {
        return List.copyOf(outbox);
    }

    public synchronized void clearOutbox() {
        outbox.clear();
    }
}
