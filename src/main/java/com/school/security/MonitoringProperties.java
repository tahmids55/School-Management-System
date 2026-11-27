package com.school.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Credentials protecting {@code /actuator/prometheus}; endpoint remains closed when either is blank.
 */
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {

    private String metricsUsername = "";
    private String metricsPassword = "";

    public String getMetricsUsername() {
        return metricsUsername;
    }

    public void setMetricsUsername(String metricsUsername) {
        this.metricsUsername = metricsUsername != null ? metricsUsername : "";
    }

    public String getMetricsPassword() {
        return metricsPassword;
    }

    public void setMetricsPassword(String metricsPassword) {
        this.metricsPassword = metricsPassword != null ? metricsPassword : "";
    }

    public boolean isConfigured() {
        return StringUtils.hasText(metricsUsername) && StringUtils.hasText(metricsPassword);
    }

    public boolean matches(String username, String password) {
        if (!isConfigured() || username == null || password == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(username), sha256(metricsUsername))
                && MessageDigest.isEqual(sha256(password), sha256(metricsPassword));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
