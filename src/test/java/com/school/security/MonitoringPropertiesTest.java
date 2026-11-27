package com.school.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringPropertiesTest {

    @Test
    void isConfigured_isFalseWhenUsernameOrPasswordIsBlank() {
        MonitoringProperties unset = new MonitoringProperties();
        assertThat(unset.isConfigured()).isFalse();
        assertThat(unset.matches("", "")).isFalse();

        MonitoringProperties onlyUsername = new MonitoringProperties();
        onlyUsername.setMetricsUsername("prometheus");
        onlyUsername.setMetricsPassword("   ");
        assertThat(onlyUsername.isConfigured()).isFalse();
        assertThat(onlyUsername.matches("prometheus", "   ")).isFalse();

        MonitoringProperties onlyPassword = new MonitoringProperties();
        onlyPassword.setMetricsUsername("");
        onlyPassword.setMetricsPassword("secret");
        assertThat(onlyPassword.isConfigured()).isFalse();
        assertThat(onlyPassword.matches("", "secret")).isFalse();
    }

    @Test
    void matches_acceptsExactCredentialsAndRejectsMismatches() {
        MonitoringProperties properties = new MonitoringProperties();
        properties.setMetricsUsername("prometheus");
        properties.setMetricsPassword("long-scrape-secret-exceeding-72-bytes-to-verify-sha256-constant-time-check-1234567890");

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.matches(
                "prometheus",
                "long-scrape-secret-exceeding-72-bytes-to-verify-sha256-constant-time-check-1234567890"
        )).isTrue();

        assertThat(properties.matches("prometheus", "wrong-secret")).isFalse();
        assertThat(properties.matches("other-user", "long-scrape-secret-exceeding-72-bytes-to-verify-sha256-constant-time-check-1234567890")).isFalse();
        assertThat(properties.matches(null, "secret")).isFalse();
        assertThat(properties.matches("prometheus", null)).isFalse();
    }
}
