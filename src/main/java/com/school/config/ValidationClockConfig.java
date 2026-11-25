package com.school.config;

import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Makes @Past, @PastOrPresent, @Future ... use the school Clock (Asia/Dhaka) instead of the server's
 * time zone. Otherwise a date entered after midnight in Dhaka could be "in the future" on a UTC server.
 */
@Configuration
public class ValidationClockConfig {

    @Bean
    public ValidationConfigurationCustomizer schoolClockForValidation(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
