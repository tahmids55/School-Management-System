package com.school.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed, validated security settings bound from {@code app.security.*}.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    @Valid
    @NotNull
    private Jwt jwt = new Jwt();

    @Min(1)
    private int maxFailedLogins = 5;

    @NotNull
    private Duration lockDuration = Duration.ofMinutes(15);

    private boolean cookieSecure = true;

    private Duration refreshReuseGrace = Duration.ofSeconds(10);

    @Min(10)
    @Max(14)
    private int bcryptCost = 12;

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public int getMaxFailedLogins() {
        return maxFailedLogins;
    }

    public void setMaxFailedLogins(int maxFailedLogins) {
        this.maxFailedLogins = maxFailedLogins;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public Duration getRefreshReuseGrace() {
        return refreshReuseGrace;
    }

    public void setRefreshReuseGrace(Duration refreshReuseGrace) {
        this.refreshReuseGrace = refreshReuseGrace;
    }

    public int getBcryptCost() {
        return bcryptCost;
    }

    public void setBcryptCost(int bcryptCost) {
        this.bcryptCost = bcryptCost;
    }

    public static class Jwt {

        @NotBlank
        @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
        private String secret;

        @NotBlank
        private String issuer = "school-management";

        @NotNull
        private Duration accessTokenTtl = Duration.ofMinutes(15);

        @NotNull
        private Duration refreshTokenTtl = Duration.ofDays(7);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }
    }
}
