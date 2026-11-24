package com.school.user;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates 12-character temporary passwords without visually ambiguous characters.
 */
@Component
public class PasswordGenerator {

    public static final int PASSWORD_LENGTH = 12;

    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALLOWED_CHARS = LETTERS + DIGITS;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate() {
        char[] chars = new char[PASSWORD_LENGTH];
        chars[0] = LETTERS.charAt(SECURE_RANDOM.nextInt(LETTERS.length()));
        chars[1] = DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length()));
        for (int i = 2; i < PASSWORD_LENGTH; i++) {
            chars[i] = ALLOWED_CHARS.charAt(SECURE_RANDOM.nextInt(ALLOWED_CHARS.length()));
        }
        for (int i = chars.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
