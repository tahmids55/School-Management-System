package com.school.auth;

import com.school.audit.AuditService;
import com.school.auth.AuthDtos.AuthSession;
import com.school.auth.AuthDtos.ChangePasswordRequest;
import com.school.auth.AuthDtos.CurrentUser;
import com.school.auth.AuthDtos.LoginRequest;
import com.school.auth.AuthDtos.TokenResponse;
import com.school.common.exception.AuthenticationFailedException;
import com.school.common.exception.BusinessRuleException;
import com.school.security.JwtService;
import com.school.security.ProfileLookup;
import com.school.security.SecurityProperties;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:00:00Z");

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditService auditService;

    @Mock
    private ProfileLookup studentLookup;

    @Mock
    private ProfileLookup teacherLookup;

    private SecurityProperties securityProperties;
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Dhaka"));
    private AuthService authService;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.setMaxFailedLogins(5);
        securityProperties.setLockDuration(Duration.ofMinutes(15));
        securityProperties.setRefreshReuseGrace(Duration.ofSeconds(10));
        securityProperties.getJwt().setSecret("test-jwt-secret-key-at-least-32-characters");

        TransactionTemplate txTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        authService = new AuthService(
                appUserRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                auditService,
                securityProperties,
                List.of(studentLookup, teacherLookup),
                txTemplate,
                clock
        );
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_success_resetsFailuresAndIssuesTokens() {
        AppUser admin = user(1L, "admin", "admin@school.edu", "$2a$12$hash", "Nasima Akter", Role.ADMIN);
        admin.setFailedLoginAttempts(3);
        admin.setLockedUntil(NOW.minusSeconds(1));
        when(appUserRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(appUserRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret123", "$2a$12$hash")).thenReturn(true);
        when(refreshTokenService.issue(admin)).thenReturn("raw-refresh-token");
        when(jwtService.generateAccessToken(admin)).thenReturn("jwt-token");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        AuthSession session = authService.login(new LoginRequest("  admin ", "secret123"));
        TokenResponse response = session.tokenResponse();

        assertThat(session.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().username()).isEqualTo("admin");
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
        assertThat(response.user().studentId()).isNull();
        assertThat(response.user().teacherId()).isNull();
        assertThat(admin.getFailedLoginAttempts()).isZero();
        assertThat(admin.getLockedUntil()).isNull();
        assertThat(admin.getLastLoginAt()).isEqualTo(NOW);
        verify(appUserRepository).save(admin);
        verify(auditService).recordAs("admin", "LOGIN_SUCCESS", "AppUser", "1", "Logged in");
        verify(studentLookup, never()).findProfileId(any(), any());
    }

    @Test
    void login_byEmail_whenIdentifierContainsAt_looksUpByEmailAndPopulatesStudentId() {
        AppUser student = user(2L, "adm-2026-001", "s@school.edu", "$2a$12$hash", "Amina", Role.STUDENT);
        when(appUserRepository.findByEmailIgnoreCase("S@SCHOOL.EDU")).thenReturn(Optional.of(student));
        when(appUserRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(student));
        when(passwordEncoder.matches("pass1234", "$2a$12$hash")).thenReturn(true);
        when(studentLookup.findProfileId(Role.STUDENT, "adm-2026-001")).thenReturn(Optional.of(42L));
        when(refreshTokenService.issue(student)).thenReturn("refresh-2");
        when(jwtService.generateAccessToken(student)).thenReturn("student-jwt");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        AuthSession session = authService.login(new LoginRequest("S@SCHOOL.EDU", "pass1234"));

        assertThat(session.tokenResponse().user().studentId()).isEqualTo(42L);
        assertThat(session.tokenResponse().user().teacherId()).isNull();
        verify(appUserRepository, never()).findByUsernameIgnoreCase(anyString());
    }

    @Test
    void login_unknownUser_givesTheSameGenericMessage() {
        when(appUserRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verify(passwordEncoder).matches(eq("wrong"), anyString());
        verify(auditService).recordAs("ghost", "LOGIN_FAILED", "AppUser", null, "User not found");
        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void login_fourthWrongPassword_doesNotLockYet() {
        AppUser user = user(3L, "teacher1", "t@school.edu", "$2a$12$hash", "Ayesha", Role.TEACHER);
        user.setFailedLoginAttempts(3);
        when(appUserRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(appUserRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("teacher1", "wrongpass")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(user.getLockedUntil()).isNull();
        verify(appUserRepository).save(user);
        verify(auditService).recordAs("teacher1", "LOGIN_FAILED", "AppUser", "3", "Invalid password");
    }

    @Test
    void login_fifthWrongPassword_locksTheAccount() {
        AppUser user = user(4L, "teacher1", "t@school.edu", "$2a$12$hash", "Ayesha", Role.TEACHER);
        user.setFailedLoginAttempts(4);
        when(appUserRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(appUserRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("teacher1", "wrongpass")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        verify(appUserRepository).save(user);
        verify(auditService).recordAs(eq("teacher1"), eq("LOGIN_FAILED"), eq("AppUser"), eq("4"), contains("locked"));
    }

    @Test
    void login_lockedAccount_isRejectedEvenWithTheRightPassword() {
        AppUser user = user(5L, "teacher1", "t@school.edu", "$2a$12$hash", "Ayesha", Role.TEACHER);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(NOW.plusSeconds(60));
        when(appUserRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(appUserRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "$2a$12$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("teacher1", "correct")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verify(auditService).recordAs("teacher1", "LOGIN_FAILED", "AppUser", "5", "Account locked");
        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).issue(any());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void login_expiredLock_allowsRightPasswordAndClearsLock() {
        AppUser user = user(6L, "teacher1", "t@school.edu", "$2a$12$hash", "Ayesha", Role.TEACHER);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(NOW);
        when(appUserRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(appUserRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "$2a$12$hash")).thenReturn(true);
        when(refreshTokenService.issue(user)).thenReturn("fresh-refresh");
        when(jwtService.generateAccessToken(user)).thenReturn("fresh-jwt");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        AuthSession session = authService.login(new LoginRequest("teacher1", "correct"));

        assertThat(session.tokenResponse().accessToken()).isEqualTo("fresh-jwt");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void login_disabledAccount_isRejectedOnlyAfterCorrectPassword() {
        AppUser disabled = user(7L, "olduser", "old@school.edu", "$2a$12$hash", "Old", Role.TEACHER);
        disabled.setEnabled(false);
        when(appUserRepository.findByUsernameIgnoreCase("olduser")).thenReturn(Optional.of(disabled));
        when(appUserRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("wrong", "$2a$12$hash")).thenReturn(false);
        when(passwordEncoder.matches("correct", "$2a$12$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("olduser", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        assertThatThrownBy(() -> authService.login(new LoginRequest("olduser", "correct")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("This account is disabled. Contact the administrator.");

        verify(auditService).recordAs("olduser", "LOGIN_FAILED", "AppUser", "7", "Account disabled");
        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void refresh_validToken_issuesNewAccessAndRefreshTokens() {
        AppUser teacher = user(8L, "t-1001", "ayesha@school.edu", "h", "Ayesha Rahman", Role.TEACHER);
        when(refreshTokenService.consume("old-raw")).thenReturn(teacher);
        when(refreshTokenService.issue(teacher)).thenReturn("new-raw");
        when(jwtService.generateAccessToken(teacher)).thenReturn("new-jwt");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(teacherLookup.findProfileId(Role.TEACHER, "t-1001")).thenReturn(Optional.of(15L));

        AuthSession session = authService.refresh("old-raw");

        assertThat(session.refreshToken()).isEqualTo("new-raw");
        assertThat(session.tokenResponse().accessToken()).isEqualTo("new-jwt");
        assertThat(session.tokenResponse().user().teacherId()).isEqualTo(15L);
    }

    @Test
    void refresh_disabledUser_revokesAllTokensAndRejects() {
        AppUser disabled = user(9L, "t-1001", "ayesha@school.edu", "h", "Ayesha Rahman", Role.TEACHER);
        disabled.setEnabled(false);
        when(refreshTokenService.consume("old-raw")).thenReturn(disabled);

        assertThatThrownBy(() -> authService.refresh("old-raw"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("This account is disabled. Contact the administrator.");

        verify(refreshTokenService).revokeAllForUser(disabled);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void logout_whenTokenFound_recordsLogoutAudit() {
        AppUser teacher = user(10L, "t-1001", "ayesha@school.edu", "h", "Ayesha Rahman", Role.TEACHER);
        when(refreshTokenService.logout("raw-token")).thenReturn(Optional.of(teacher));

        authService.logout("raw-token");

        verify(auditService).recordAs("t-1001", "LOGOUT", "AppUser", "10", "Logged out");
    }

    @Test
    void currentUser_whenTeacherWithProfileLookup_returnsTeacherId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t-1001", "n/a", List.of())
        );
        AppUser teacher = user(11L, "t-1001", "ayesha@school.edu", "h", "Ayesha Rahman", Role.TEACHER);
        when(appUserRepository.findByUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(teacher));
        when(teacherLookup.findProfileId(Role.TEACHER, "t-1001")).thenReturn(Optional.of(15L));

        CurrentUser currentUser = authService.currentUser();

        assertThat(currentUser.username()).isEqualTo("t-1001");
        assertThat(currentUser.teacherId()).isEqualTo(15L);
        assertThat(currentUser.studentId()).isNull();
    }

    @Test
    void currentUser_whenDisabled_throwsAuthenticationFailed() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t-1001", "n/a", List.of())
        );
        AppUser disabled = user(12L, "t-1001", "ayesha@school.edu", "h", "Ayesha Rahman", Role.TEACHER);
        disabled.setEnabled(false);
        when(appUserRepository.findByUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.currentUser())
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("This account is disabled. Contact the administrator.");
    }

    @Test
    void changePassword_wrongCurrentPassword_isRejected() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t-1001", "n/a", List.of())
        );
        AppUser teacher = user(13L, "t-1001", "ayesha@school.edu", "$2a$12$oldhash", "Ayesha Rahman", Role.TEACHER);
        when(appUserRepository.findByUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(teacher));
        when(passwordEncoder.matches("WrongPass1", "$2a$12$oldhash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(new ChangePasswordRequest("WrongPass1", "NewPass123")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Current password is incorrect");

        verify(appUserRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void changePassword_sameAsCurrentPassword_isRejected() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t-1001", "n/a", List.of())
        );
        AppUser teacher = user(14L, "t-1001", "ayesha@school.edu", "$2a$12$oldhash", "Ayesha Rahman", Role.TEACHER);
        when(appUserRepository.findByUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(teacher));
        when(passwordEncoder.matches("SamePass1", "$2a$12$oldhash")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(new ChangePasswordRequest("SamePass1", "SamePass1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("New password must be different from the current password");

        verify(appUserRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void changePassword_success_clearsFlagAndLogsOutOtherDevices() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t-1001", "n/a", List.of())
        );
        AppUser teacher = user(15L, "t-1001", "ayesha@school.edu", "$2a$12$oldhash", "Ayesha Rahman", Role.TEACHER);
        teacher.setMustChangePassword(true);
        when(appUserRepository.findByUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(teacher));
        when(passwordEncoder.matches("TempPass123", "$2a$12$oldhash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "$2a$12$oldhash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$12$newhash");

        authService.changePassword(new ChangePasswordRequest("TempPass123", "NewPass123"));

        assertThat(teacher.getPasswordHash()).isEqualTo("$2a$12$newhash");
        assertThat(teacher.isMustChangePassword()).isFalse();
        verify(appUserRepository).save(teacher);
        verify(refreshTokenService).revokeAllForUser(teacher);
        verify(auditService).recordAs("t-1001", "PASSWORD_CHANGED", "AppUser", "15", "Password changed");
    }

    @Test
    void changePassword_unauthenticated_throwsAuthenticationFailed() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> authService.changePassword(new ChangePasswordRequest("OldPass1", "NewPass123")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Session expired. Please log in again.");
    }

    private static AppUser user(
            Long id,
            String username,
            String email,
            String passwordHash,
            String fullName,
            Role role
    ) {
        AppUser u = new AppUser(username, email, passwordHash, fullName, role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
