package com.school.security;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(PropertiesTestConfig.class, SecurityPropertiesValidator.class);

    @Test
    void validProperties_passValidation() {
        SecurityProperties properties = validProperties();

        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void secretUnder32Characters_failsValidationWithExactMessage() {
        SecurityProperties properties = validProperties();
        properties.getJwt().setSecret("too-short-secret");

        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("JWT_SECRET must be at least 32 characters");
    }

    @Test
    void bcryptCostOutside10To14_failsValidation() {
        SecurityProperties tooLow = validProperties();
        tooLow.setBcryptCost(9);
        assertThat(validator.validate(tooLow)).isNotEmpty();

        SecurityProperties tooHigh = validProperties();
        tooHigh.setBcryptCost(15);
        assertThat(validator.validate(tooHigh)).isNotEmpty();

        SecurityProperties cost10 = validProperties();
        cost10.setBcryptCost(10);
        assertThat(validator.validate(cost10)).isEmpty();

        SecurityProperties cost14 = validProperties();
        cost14.setBcryptCost(14);
        assertThat(validator.validate(cost14)).isEmpty();
    }

    @Test
    void validator_refusesPlaceholderSecretInProd_butAllowsInDev() {
        SecurityProperties properties = validProperties();
        properties.getJwt().setSecret(SecurityPropertiesValidator.ENV_EXAMPLE_PLACEHOLDER);

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        SecurityPropertiesValidator prodValidator = new SecurityPropertiesValidator(properties, prodEnv);
        assertThatThrownBy(prodValidator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");

        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        SecurityPropertiesValidator devValidator = new SecurityPropertiesValidator(properties, devEnv);
        assertThatCode(devValidator::validate).doesNotThrowAnyException();
    }

    @Test
    void validator_acceptsStrongRandomSecretInProd() {
        SecurityProperties properties = validProperties();
        properties.getJwt().setSecret("k9M#xP2vL8qR4tW7yN1zC5bF0hJ3dG6sA8eU");

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        SecurityPropertiesValidator prodValidator = new SecurityPropertiesValidator(properties, prodEnv);

        assertThatCode(prodValidator::validate).doesNotThrowAnyException();
    }

    @Test
    void contextStartup_failsWhenSecretTooShortOrBcryptCostInvalidOrProdPlaceholderUsed() {
        contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret=too-short",
                        "app.security.jwt.issuer=school-management",
                        "app.security.jwt.access-token-ttl=15m",
                        "app.security.jwt.refresh-token-ttl=7d",
                        "app.security.max-failed-logins=5",
                        "app.security.lock-duration=15m"
                )
                .run(ctx -> assertThat(ctx).hasFailed());

        contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret=test-jwt-secret-key-at-least-32-characters",
                        "app.security.jwt.issuer=school-management",
                        "app.security.jwt.access-token-ttl=15m",
                        "app.security.jwt.refresh-token-ttl=7d",
                        "app.security.max-failed-logins=5",
                        "app.security.lock-duration=15m",
                        "app.security.bcrypt-cost=9"
                )
                .run(ctx -> assertThat(ctx).hasFailed());

        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.security.jwt.secret=" + SecurityPropertiesValidator.ENV_EXAMPLE_PLACEHOLDER,
                        "app.security.jwt.issuer=school-management",
                        "app.security.jwt.access-token-ttl=15m",
                        "app.security.jwt.refresh-token-ttl=7d",
                        "app.security.max-failed-logins=5",
                        "app.security.lock-duration=15m"
                )
                .run(ctx -> assertThat(ctx).hasFailed());

        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.security.jwt.secret=k9MxP2vL8qR4tW7yN1zC5bF0hJ3dG6sA8eU",
                        "app.security.jwt.issuer=school-management",
                        "app.security.jwt.access-token-ttl=15m",
                        "app.security.jwt.refresh-token-ttl=7d",
                        "app.security.max-failed-logins=5",
                        "app.security.lock-duration=15m"
                )
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Configuration
    @EnableConfigurationProperties(SecurityProperties.class)
    static class PropertiesTestConfig {
    }

    private static SecurityProperties validProperties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setMaxFailedLogins(5);
        properties.setLockDuration(Duration.ofMinutes(15));
        properties.setCookieSecure(true);
        properties.setRefreshReuseGrace(Duration.ofSeconds(10));
        properties.setBcryptCost(12);
        properties.getJwt().setSecret("test-jwt-secret-key-at-least-32-characters");
        properties.getJwt().setIssuer("school-management");
        properties.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));
        properties.getJwt().setRefreshTokenTtl(Duration.ofDays(7));
        return properties;
    }
}
