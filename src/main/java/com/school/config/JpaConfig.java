package com.school.config;

import com.school.common.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    private static final int MAX_AUDITOR_LENGTH = 50;

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            String actor = SecurityUtils.currentUsernameOrSystem();
            if (actor.length() > MAX_AUDITOR_LENGTH) {
                actor = actor.substring(0, MAX_AUDITOR_LENGTH);
            }
            return Optional.of(actor);
        };
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
