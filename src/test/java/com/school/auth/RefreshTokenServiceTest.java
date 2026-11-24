package com.school.auth;

import com.school.audit.AuditService;
import com.school.common.exception.AuthenticationFailedException;
import com.school.security.SecurityProperties;
import com.school.user.AppUser;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditService auditService;

    private SecurityProperties securityProperties;
    private Clock clock;
    private RefreshTokenService refreshTokenService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getJwt().setSecret("test-jwt-secret-key-at-least-32-characters");
        securityProperties.getJwt().setRefreshTokenTtl(Duration.ofDays(7));
        securityProperties.setRefreshReuseGrace(Duration.ofSeconds(10));
        clock = Clock.fixed(NOW, ZoneId.of("Asia/Dhaka"));

        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                securityProperties,
                auditService,
                clock
        );

        user = new AppUser("t-1001", "ayesha@school.edu", "hash", "Ayesha Rahman", Role.TEACHER);
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void issue_generates32RandomBytesBase64Url_andStoresSha256HexWith7DayExpiry() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawToken = refreshTokenService.issue(user);

        byte[] decoded = Base64.getUrlDecoder().decode(rawToken);
        assertThat(decoded).hasSize(32);
        assertThat(rawToken).doesNotContain("=");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTokenHash()).isEqualTo(refreshTokenService.hash(rawToken));
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void consume_validToken_revokesItAndReturnsTheUser() {
        String rawToken = "valid-raw-token";
        String hash = refreshTokenService.hash(rawToken);
        RefreshToken token = new RefreshToken(user, hash, NOW.plusSeconds(3600), NOW.minusSeconds(60));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));

        AppUser result = refreshTokenService.consume(rawToken);

        assertThat(result).isSameAs(user);
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        verify(refreshTokenRepository).save(token);
        verify(refreshTokenRepository, never()).deleteAllByUser(any());
    }

    @Test
    void consume_tokenReusedLongAfterRotation_revokesEveryTokenOfTheUser() {
        String rawToken = "stolen-rotated-token";
        String hash = refreshTokenService.hash(rawToken);
        RefreshToken token = new RefreshToken(user, hash, NOW.plusSeconds(3600), NOW.minusSeconds(600));
        token.setRevokedAt(NOW.minusSeconds(11));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.consume(rawToken))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Session expired. Please log in again.");

        verify(refreshTokenRepository).deleteAllByUser(user);
        verify(auditService).recordAs(
                eq("t-1001"),
                eq(RefreshTokenService.AUDIT_REFRESH_THEFT),
                eq("AppUser"),
                eq("7"),
                any()
        );
    }

    @Test
    void consume_tokenReusedWithinGracePeriod_isAParallelTabNotTheft() {
        String rawToken = "parallel-tab-token";
        String hash = refreshTokenService.hash(rawToken);
        Instant originalRevokedAt = NOW.minusSeconds(5);
        RefreshToken token = new RefreshToken(user, hash, NOW.plusSeconds(3600), NOW.minusSeconds(60));
        token.setRevokedAt(originalRevokedAt);
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));

        AppUser result = refreshTokenService.consume(rawToken);

        assertThat(result).isSameAs(user);
        assertThat(token.getRevokedAt()).isEqualTo(originalRevokedAt);
        verify(refreshTokenRepository, never()).deleteAllByUser(any());
        verify(auditService, never()).recordAs(any(), any(), any(), any(), any());
    }

    @Test
    void consume_expiredToken_isRejected() {
        String rawToken = "expired-token";
        String hash = refreshTokenService.hash(rawToken);
        RefreshToken token = new RefreshToken(user, hash, NOW.minusSeconds(1), NOW.minusSeconds(3600));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.consume(rawToken))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Session expired. Please log in again.");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void consume_missingOrDeletedToken_isRejected() {
        assertThatThrownBy(() -> refreshTokenService.consume(null))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Session expired. Please log in again.");

        assertThatThrownBy(() -> refreshTokenService.consume("   "))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Session expired. Please log in again.");

        String hash = refreshTokenService.hash("deleted-token");
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.consume("deleted-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Session expired. Please log in again.");
    }

    @Test
    void logout_activeToken_deletesPresentedAndRotatedTokens() {
        String rawToken = "active-token";
        String hash = refreshTokenService.hash(rawToken);
        RefreshToken token = new RefreshToken(user, hash, NOW.plusSeconds(3600), NOW.minusSeconds(60));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));

        Optional<AppUser> result = refreshTokenService.logout(rawToken);

        assertThat(result).contains(user);
        verify(refreshTokenRepository).delete(token);
        verify(refreshTokenRepository).flush();
        verify(refreshTokenRepository).deleteRotatedByUser(user);
        verify(refreshTokenRepository, never()).deleteAllByUser(any());
    }

    @Test
    void logout_alreadyRotatedToken_deletesAllTokensOfUser() {
        String rawToken = "rotated-token";
        String hash = refreshTokenService.hash(rawToken);
        RefreshToken token = new RefreshToken(user, hash, NOW.plusSeconds(3600), NOW.minusSeconds(60));
        token.setRevokedAt(NOW.minusSeconds(2));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));

        Optional<AppUser> result = refreshTokenService.logout(rawToken);

        assertThat(result).contains(user);
        verify(refreshTokenRepository).deleteAllByUser(user);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void logout_withoutToken_isNoOp() {
        assertThat(refreshTokenService.logout(null)).isEmpty();
        assertThat(refreshTokenService.logout("")).isEmpty();
        verify(refreshTokenRepository, never()).findByTokenHashForUpdate(any());
    }

    @Test
    void revokeAllForUser_deletesAllRowsForUser() {
        refreshTokenService.revokeAllForUser(user);
        verify(refreshTokenRepository).deleteAllByUser(user);
    }

    @Test
    void hash_isSha256Hex_andNeverTheRawValue() {
        String raw = "sample-refresh-token-value";
        String digest = refreshTokenService.hash(raw);

        assertThat(digest).isNotEqualTo(raw);
        assertThat(digest).matches("^[0-9a-f]{64}$");
        assertThat(refreshTokenService.hash(raw)).isEqualTo(digest);
        assertThat(refreshTokenService.hash("different-token")).isNotEqualTo(digest);
    }
}
