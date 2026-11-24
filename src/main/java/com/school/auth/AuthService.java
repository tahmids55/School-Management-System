package com.school.auth;

import com.school.audit.AuditService;
import com.school.auth.AuthDtos.AuthSession;
import com.school.auth.AuthDtos.ChangePasswordRequest;
import com.school.auth.AuthDtos.CurrentUser;
import com.school.auth.AuthDtos.LoginRequest;
import com.school.auth.AuthDtos.TokenResponse;
import com.school.common.SecurityUtils;
import com.school.common.exception.AuthenticationFailedException;
import com.school.common.exception.BusinessRuleException;
import com.school.security.JwtService;
import com.school.security.ProfileLookup;
import com.school.security.SecurityProperties;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Handles stateless authentication, refresh-token rotation, logout, lockout, and current-user profile resolution.
 */
@Service
public class AuthService {

    public static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";
    public static final String ACCOUNT_DISABLED_MESSAGE =
            "This account is disabled. Contact the administrator.";
    public static final String AUTH_REQUIRED_MESSAGE = "Authentication required. Please log in again.";

    public static final String AUDIT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String AUDIT_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String AUDIT_LOGOUT = "LOGOUT";
    public static final String AUDIT_PASSWORD_CHANGED = "PASSWORD_CHANGED";

    public static final String CURRENT_PASSWORD_INCORRECT_MESSAGE = "Current password is incorrect";
    public static final String NEW_PASSWORD_SAME_MESSAGE = "New password must be different from the current password";

    private static final String FALLBACK_DUMMY_HASH =
            "$2a$12$7EqJtq98hPqEX7fNZaFWoOhi5L0v7sM5y8qY7wQ8xZ6a5b4c3d2e1";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;
    private final List<ProfileLookup> profileLookups;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            SecurityProperties securityProperties,
            List<ProfileLookup> profileLookups,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
        this.profileLookups = profileLookups;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        String encodedDummy = passwordEncoder.encode("dummy-constant-time-check");
        this.dummyPasswordHash = encodedDummy != null ? encodedDummy : FALLBACK_DUMMY_HASH;
    }

    public AuthSession login(LoginRequest request) {
        String identifier = request.username().trim();
        Optional<AppUser> maybeSnapshot = findByIdentifier(identifier);

        if (maybeSnapshot.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            transactionTemplate.executeWithoutResult(status ->
                    auditService.recordAs(identifier, AUDIT_LOGIN_FAILED, "AppUser", null, "User not found")
            );
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }

        AppUser snapshot = maybeSnapshot.get();
        String snapshotHash = snapshot.getPasswordHash();
        // Run BCrypt outside the DB transaction so parallel login attempts do not hold pool connections or row locks.
        boolean precomputedMatch = passwordEncoder.matches(request.password(), snapshotHash);

        LoginOutcome outcome = transactionTemplate.execute(status ->
                completeLoginInTransaction(snapshot.getId(), snapshotHash, request.password(), precomputedMatch)
        );

        if (outcome == null) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }
        if (outcome.failureMessage() != null) {
            throw new AuthenticationFailedException(outcome.failureMessage());
        }
        return outcome.session();
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthSession refresh(String rawRefreshToken) {
        AppUser user = refreshTokenService.consume(rawRefreshToken);
        if (!user.isEnabled()) {
            refreshTokenService.revokeAllForUser(user);
            auditService.recordAs(
                    user.getUsername(),
                    AUDIT_LOGIN_FAILED,
                    "AppUser",
                    entityId(user),
                    "Account disabled"
            );
            throw new AuthenticationFailedException(ACCOUNT_DISABLED_MESSAGE);
        }
        String nextRefreshToken = refreshTokenService.issue(user);
        return issueSession(user, nextRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.logout(rawRefreshToken).ifPresent(user ->
                auditService.recordAs(
                        user.getUsername(),
                        AUDIT_LOGOUT,
                        "AppUser",
                        entityId(user),
                        "Logged out"
                )
        );
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser() {
        String username = SecurityUtils.currentUsername()
                .orElseThrow(() -> new AuthenticationFailedException(AUTH_REQUIRED_MESSAGE));
        AppUser user = appUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new AuthenticationFailedException(AUTH_REQUIRED_MESSAGE));
        if (!user.isEnabled()) {
            throw new AuthenticationFailedException(ACCOUNT_DISABLED_MESSAGE);
        }
        return toCurrentUser(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String username = SecurityUtils.currentUsername()
                .orElseThrow(() -> new AuthenticationFailedException(RefreshTokenService.SESSION_EXPIRED_MESSAGE));
        AppUser user = appUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new AuthenticationFailedException(RefreshTokenService.SESSION_EXPIRED_MESSAGE));
        if (!user.isEnabled()) {
            throw new AuthenticationFailedException(ACCOUNT_DISABLED_MESSAGE);
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException(CURRENT_PASSWORD_INCORRECT_MESSAGE);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException(NEW_PASSWORD_SAME_MESSAGE);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
        auditService.recordAs(
                user.getUsername(),
                AUDIT_PASSWORD_CHANGED,
                "AppUser",
                entityId(user),
                "Password changed"
        );
    }

    private LoginOutcome completeLoginInTransaction(
            Long userId,
            String snapshotHash,
            String rawPassword,
            boolean precomputedMatch
    ) {
        AppUser user = appUserRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return LoginOutcome.failure(INVALID_CREDENTIALS_MESSAGE);
        }

        Instant now = clock.instant();
        if (isLocked(user, now)) {
            auditService.recordAs(
                    user.getUsername(),
                    AUDIT_LOGIN_FAILED,
                    "AppUser",
                    entityId(user),
                    "Account locked"
            );
            return LoginOutcome.failure(INVALID_CREDENTIALS_MESSAGE);
        }

        if (user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        boolean passwordMatches = snapshotHash.equals(user.getPasswordHash())
                ? precomputedMatch
                : passwordEncoder.matches(rawPassword, user.getPasswordHash());

        if (!passwordMatches) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            String details;
            if (attempts >= securityProperties.getMaxFailedLogins()) {
                user.setLockedUntil(now.plus(securityProperties.getLockDuration()));
                details = "Account locked after " + attempts + " failed login attempts";
            } else {
                details = "Invalid password";
            }
            appUserRepository.save(user);
            auditService.recordAs(user.getUsername(), AUDIT_LOGIN_FAILED, "AppUser", entityId(user), details);
            return LoginOutcome.failure(INVALID_CREDENTIALS_MESSAGE);
        }

        if (!user.isEnabled()) {
            auditService.recordAs(
                    user.getUsername(),
                    AUDIT_LOGIN_FAILED,
                    "AppUser",
                    entityId(user),
                    "Account disabled"
            );
            return LoginOutcome.failure(ACCOUNT_DISABLED_MESSAGE);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        appUserRepository.save(user);
        auditService.recordAs(
                user.getUsername(),
                AUDIT_LOGIN_SUCCESS,
                "AppUser",
                entityId(user),
                "Logged in"
        );

        String refreshToken = refreshTokenService.issue(user);
        return LoginOutcome.success(issueSession(user, refreshToken));
    }

    private Optional<AppUser> findByIdentifier(String identifier) {
        return identifier.contains("@")
                ? appUserRepository.findByEmailIgnoreCase(identifier)
                : appUserRepository.findByUsernameIgnoreCase(identifier);
    }

    private AuthSession issueSession(AppUser user, String rawRefreshToken) {
        String accessToken = jwtService.generateAccessToken(user);
        TokenResponse tokenResponse = TokenResponse.from(
                accessToken,
                jwtService.accessTokenExpiresInSeconds(),
                toCurrentUser(user)
        );
        return new AuthSession(tokenResponse, rawRefreshToken);
    }

    private static boolean isLocked(AppUser user, Instant now) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    }

    private CurrentUser toCurrentUser(AppUser user) {
        Long studentId = resolveProfileId(Role.STUDENT, user);
        Long teacherId = resolveProfileId(Role.TEACHER, user);
        return CurrentUser.from(user, studentId, teacherId);
    }

    private Long resolveProfileId(Role targetRole, AppUser user) {
        if (user.getRole() != targetRole) {
            return null;
        }
        return profileLookups.stream()
                .map(lookup -> lookup.findProfileId(targetRole, user.getUsername()))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    private static String entityId(AppUser user) {
        return user.getId() != null ? user.getId().toString() : null;
    }

    private record LoginOutcome(AuthSession session, String failureMessage) {
        private static LoginOutcome success(AuthSession session) {
            return new LoginOutcome(session, null);
        }

        private static LoginOutcome failure(String failureMessage) {
            return new LoginOutcome(null, failureMessage);
        }
    }
}
