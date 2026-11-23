package com.school.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.config.SecurityConfig;
import com.school.user.AppUser;
import com.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private final SecurityConfig securityConfig = new SecurityConfig(new ObjectMapper());

    @Test
    void generateAndDecodeToken_containsExpectedClaimsAndRolePrefix() {
        SecurityProperties properties = properties("school-management", Duration.ofMinutes(15));
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Dhaka"));

        JwtEncoder encoder = securityConfig.jwtEncoder(properties);
        JwtDecoder decoder = securityConfig.jwtDecoder(properties, clock);
        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
        JwtService jwtService = new JwtService(encoder, properties, clock);

        AppUser user = new AppUser("admin", "admin@school.edu", "hash", "Nasima Akter", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 7L);

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("school-management");
        assertThat(decoded.getSubject()).isEqualTo("admin");
        assertThat(((Number) decoded.getClaim("uid")).longValue()).isEqualTo(7L);
        assertThat(decoded.getClaimAsString("name")).isEqualTo("Nasima Akter");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(jwtService.accessTokenExpiresInSeconds()).isEqualTo(900L);

        AbstractAuthenticationToken auth = converter.convert(decoded);
        assertThat(auth.getName()).isEqualTo("admin");
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void decodeToken_withWrongIssuerOrExpiredTimestamp_isRejected() {
        SecurityProperties issuerOne = properties("school-management", Duration.ofMinutes(15));
        SecurityProperties issuerTwo = properties("other-issuer", Duration.ofMinutes(15));

        Clock issueClock = Clock.fixed(NOW, ZoneId.of("Asia/Dhaka"));
        JwtService jwtService = new JwtService(securityConfig.jwtEncoder(issuerOne), issuerOne, issueClock);
        AppUser user = new AppUser("teacher1", "t@school.edu", "hash", "Ayesha", Role.TEACHER);
        ReflectionTestUtils.setField(user, "id", 9L);
        String token = jwtService.generateAccessToken(user);

        JwtDecoder wrongIssuerDecoder = securityConfig.jwtDecoder(issuerTwo, issueClock);
        assertThatThrownBy(() -> wrongIssuerDecoder.decode(token))
                .isInstanceOf(JwtException.class);

        Clock expiredClock = Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneId.of("Asia/Dhaka"));
        JwtDecoder expiredDecoder = securityConfig.jwtDecoder(issuerOne, expiredClock);
        assertThatThrownBy(() -> expiredDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    private static SecurityProperties properties(String issuer, Duration accessTtl) {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("test-jwt-secret-key-at-least-32-characters");
        properties.getJwt().setIssuer(issuer);
        properties.getJwt().setAccessTokenTtl(accessTtl);
        properties.getJwt().setRefreshTokenTtl(Duration.ofDays(7));
        return properties;
    }
}
