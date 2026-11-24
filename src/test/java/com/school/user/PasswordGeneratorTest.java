package com.school.user;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    private static final String LOOK_ALIKES = "0O1lIoi";

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void generate_produces12CharsWithLetterDigitAndNoLookAlikes() {
        Set<String> generated = new HashSet<>();

        for (int i = 0; i < 500; i++) {
            String password = generator.generate();
            assertThat(password).hasSize(12);
            assertThat(password).matches("^(?=.*[A-Za-z])(?=.*\\d).{12}$");
            for (char ch : LOOK_ALIKES.toCharArray()) {
                assertThat(password).doesNotContain(String.valueOf(ch));
            }
            generated.add(password);
        }

        assertThat(generated).hasSizeGreaterThan(490);
    }
}
