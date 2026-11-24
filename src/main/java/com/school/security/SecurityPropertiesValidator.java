package com.school.security;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Fails fast when the JWT secret is too short in bytes or still uses a placeholder in prod.
 */
@Component
public class SecurityPropertiesValidator {

    public static final String ENV_EXAMPLE_PLACEHOLDER =
            "replace-with-random-secret-before-prod-at-least-32-chars";

    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "replace-with",
            "replace-me",
            "change-me",
            "changeme",
            "change-this",
            "placeholder",
            "your-secret",
            "dev-only"
    );

    private final SecurityProperties securityProperties;
    private final Environment environment;

    public SecurityPropertiesValidator(SecurityProperties securityProperties, Environment environment) {
        this.securityProperties = securityProperties;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        String secret = securityProperties.getJwt().getSecret();
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        if (environment.acceptsProfiles(Profiles.of("prod")) && isPlaceholder(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET must not use the .env.example placeholder in the prod profile"
            );
        }
    }

    static boolean isPlaceholder(String secret) {
        String normalized = secret.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(ENV_EXAMPLE_PLACEHOLDER)) {
            return true;
        }
        return PLACEHOLDER_MARKERS.stream().anyMatch(normalized::contains);
    }
}
