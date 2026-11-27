package com.school.notification;

/**
 * Who hears about a student event: the student's login (null when the student has none)
 * and the guardian's phone for the SMS.
 */
public record Recipient(
        Long userId,
        String firstName,
        String guardianPhone
) {
}
