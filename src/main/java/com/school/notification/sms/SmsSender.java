package com.school.notification.sms;

/**
 * What the school needs from any SMS provider. The rest of the code depends only on this interface,
 * so a real provider can replace the fake one by writing one new adapter.
 */
public interface SmsSender {

    /**
     * Sends a text to a phone number. NEVER throws: an SMS is a nice-to-have, and a failure must not
     * undo or break the action that caused it.
     *
     * @return true when the provider accepted the message
     */
    boolean send(String phoneNumber, String text);
}
