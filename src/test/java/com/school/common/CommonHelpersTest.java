package com.school.common;

import com.school.config.JpaConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonHelpersTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pageResponse_mapsContentAndMetadata() {
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page, Object::toString);

        assertThat(response.content()).containsExactly("1", "2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(PageResponse.MAX_PAGE_SIZE).isEqualTo(200);
    }

    @Test
    void securityUtils_whenUnauthenticatedOrAnonymous_returnsEmptyAndSystem() {
        assertThat(SecurityUtils.currentUsername()).isEmpty();
        assertThat(SecurityUtils.currentUsernameOrSystem()).isEqualTo("system");
        assertThat(SecurityUtils.hasRole("ADMIN")).isFalse();

        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        );

        assertThat(SecurityUtils.currentUsername()).isEmpty();
        assertThat(SecurityUtils.currentUsernameOrSystem()).isEqualTo("system");
    }

    @Test
    void securityUtils_whenAuthenticatedWithJwt_readsUsernameRoleAndUid() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("nasima")
                .claim("uid", 7L)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityUtils.currentUsername()).contains("nasima");
        assertThat(SecurityUtils.currentUsernameOrSystem()).isEqualTo("nasima");
        assertThat(SecurityUtils.currentUserId()).contains(7L);
        assertThat(SecurityUtils.hasRole("ADMIN")).isTrue();
        assertThat(SecurityUtils.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(SecurityUtils.hasRole("TEACHER")).isFalse();
    }

    @Test
    void securityUtils_whenUsernamePasswordAuth_readsUsernameWithoutUid() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEACHER")))
        );

        assertThat(SecurityUtils.currentUsername()).contains("teacher1");
        assertThat(SecurityUtils.currentUserId()).isEmpty();
        assertThat(SecurityUtils.hasRole("TEACHER")).isTrue();
    }

    @Test
    void jpaConfig_auditorTruncatesAt50AndDateTimeUsesClock() {
        JpaConfig jpaConfig = new JpaConfig();
        Instant fixedInstant = Instant.parse("2026-11-23T15:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Dhaka"));

        assertThat(jpaConfig.auditorProvider().getCurrentAuditor()).contains("system");
        assertThat(jpaConfig.auditingDateTimeProvider(fixedClock).getNow()).contains(fixedInstant);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u".repeat(70), "n/a", List.of())
        );
        assertThat(jpaConfig.auditorProvider().getCurrentAuditor())
                .hasValueSatisfying(name -> assertThat(name).hasSize(50));
    }
}
