package com.school.auth;

import com.school.audit.AuditService;
import com.school.auth.AuthDtos.CurrentUser;
import com.school.auth.AuthDtos.LoginRequest;
import com.school.auth.AuthDtos.TokenResponse;
import com.school.common.SecurityUtils;
import com.school.common.exception.AuthenticationFailedException;
import com.school.security.JwtService;
import com.school.security.ProfileLookup;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Handles stateless authentication and current-user profile resolution.
 */
@Service
public class AuthService {

    public static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";
    public static final String ACCOUNT_DISABLED_MESSAGE =
            "This account is disabled. Contact the administrator.";
    public static final String AUTH_REQUIRED_MESSAGE = "Authentication required. Please log in again.";

    public static final String AUDIT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String AUDIT_LOGIN_FAILED = "LOGIN_FAILED";

    private static final String FALLBACK_DUMMY_HASH =
            "$2a$12$7EqJtq98hPqEX7fNZaFWoOhi5L0v7sM5y8qY7wQ8xZ6a5b4c3d2e1";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final List<ProfileLookup> profileLookups;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            List<ProfileLookup> profileLookups,
            Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.profileLookups = profileLookups;
        this.clock = clock;
        String encodedDummy = passwordEncoder.encode("dummy-constant-time-check");
        this.dummyPasswordHash = encodedDummy != null ? encodedDummy : FALLBACK_DUMMY_HASH;
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public TokenResponse login(LoginRequest request) {
        String identifier = request.username().trim();
        Optional<AppUser> maybeUser = identifier.contains("@")
                ? appUserRepository.findByEmailIgnoreCase(identifier)
                : appUserRepository.findByUsernameIgnoreCase(identifier);

        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            auditService.recordAs(identifier, AUDIT_LOGIN_FAILED, "AppUser", null, "User not found");
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }

        AppUser user = maybeUser.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.recordAs(
                    user.getUsername(),
                    AUDIT_LOGIN_FAILED,
                    "AppUser",
                    entityId(user),
                    "Invalid password"
            );
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (!user.isEnabled()) {
            auditService.recordAs(
                    user.getUsername(),
                    AUDIT_LOGIN_FAILED,
                    "AppUser",
                    entityId(user),
                    "Account disabled"
            );
            throw new AuthenticationFailedException(ACCOUNT_DISABLED_MESSAGE);
        }

        user.setLastLoginAt(clock.instant());
        appUserRepository.save(user);
        auditService.recordAs(
                user.getUsername(),
                AUDIT_LOGIN_SUCCESS,
                "AppUser",
                entityId(user),
                "Logged in"
        );

        String accessToken = jwtService.generateAccessToken(user);
        return TokenResponse.from(accessToken, jwtService.accessTokenExpiresInSeconds(), toCurrentUser(user));
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
}
