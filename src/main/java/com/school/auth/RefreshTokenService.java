package com.school.auth;

import com.school.audit.AuditService;
import com.school.common.exception.AuthenticationFailedException;
import com.school.security.SecurityProperties;
import com.school.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues, rotates, detects reuse of, and revokes opaque refresh tokens.
 */
@Service
public class RefreshTokenService {

    public static final String SESSION_EXPIRED_MESSAGE = "Session expired. Please log in again.";
    public static final String SHA_256_UNAVAILABLE_MESSAGE = "SHA-256 not available";
    public static final String AUDIT_REFRESH_THEFT = "REFRESH_THEFT_DETECTED";

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties securityProperties;
    private final AuditService auditService;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            SecurityProperties securityProperties,
            AuditService auditService,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityProperties = securityProperties;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public String issue(AppUser user) {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(securityProperties.getJwt().getRefreshTokenTtl());
        RefreshToken token = new RefreshToken(user, hash(rawToken), expiresAt, now);
        refreshTokenRepository.save(token);
        return rawToken;
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AppUser consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationFailedException(SESSION_EXPIRED_MESSAGE);
        }

        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new AuthenticationFailedException(SESSION_EXPIRED_MESSAGE));

        Instant now = clock.instant();
        if (token.isExpired(now)) {
            throw new AuthenticationFailedException(SESSION_EXPIRED_MESSAGE);
        }

        AppUser user = token.getUser();
        if (token.isRevoked()) {
            Instant graceDeadline = token.getRevokedAt().plus(securityProperties.getRefreshReuseGrace());
            if (now.isBefore(graceDeadline)) {
                return user;
            }
            refreshTokenRepository.deleteAllByUser(user);
            auditService.recordAs(
                    user.getUsername(),
                    AUDIT_REFRESH_THEFT,
                    "AppUser",
                    entityId(user),
                    "Refresh token reused outside grace window; all sessions revoked"
            );
            throw new AuthenticationFailedException(SESSION_EXPIRED_MESSAGE);
        }

        token.setRevokedAt(now);
        refreshTokenRepository.save(token);
        return user;
    }

    @Transactional
    public Optional<AppUser> logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> maybeToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken));
        if (maybeToken.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken token = maybeToken.get();
        AppUser user = token.getUser();
        if (token.isRevoked()) {
            refreshTokenRepository.deleteAllByUser(user);
        } else {
            refreshTokenRepository.delete(token);
            refreshTokenRepository.flush();
            refreshTokenRepository.deleteRotatedByUser(user);
        }
        return Optional.of(user);
    }

    @Transactional
    public void revokeAllForUser(AppUser user) {
        if (user == null) {
            return;
        }
        refreshTokenRepository.deleteAllByUser(user);
    }

    public String hash(String rawToken) {
        return sha256Hex(rawToken);
    }

    static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(SHA_256_UNAVAILABLE_MESSAGE, ex);
        }
    }

    private static String entityId(AppUser user) {
        return user.getId() != null ? user.getId().toString() : null;
    }
}
