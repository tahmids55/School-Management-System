package com.school.auth;

import com.school.audit.AuditService;
import com.school.auth.AuthDtos.CurrentUser;
import com.school.auth.AuthDtos.LoginRequest;
import com.school.auth.AuthDtos.TokenResponse;
import com.school.common.exception.AuthenticationFailedException;
import com.school.security.JwtService;
import com.school.security.ProfileLookup;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T15:00:00Z");

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditService auditService;

    @Mock
    private ProfileLookup studentLookup;

    @Mock
    private ProfileLookup teacherLookup;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Dhaka"));

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                appUserRepository,
                passwordEncoder,
                jwtService,
                auditService,
                List.of(studentLookup, teacherLookup),
                clock
        );
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_byUsername_issuesTokenUpdatesLastLoginAndAuditsSuccess() {
        AppUser admin = new AppUser("admin", "admin@school.edu", "$2a$12$hash", "Nasima Akter", Role.ADMIN);
        when(appUserRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret123", "$2a$12$hash")).thenReturn(true);
        when(jwtService.generateAccessToken(admin)).thenReturn("jwt-token");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        TokenResponse response = authService.login(new LoginRequest("  admin ", "secret123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().username()).isEqualTo("admin");
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
        assertThat(response.user().studentId()).isNull();
        assertThat(response.user().teacherId()).isNull();
        assertThat(admin.getLastLoginAt()).isEqualTo(NOW);
        verify(appUserRepository).save(admin);
        verify(auditService).recordAs("admin", "LOGIN_SUCCESS", "AppUser", null, "Logged in");
        verify(studentLookup, never()).findProfileId(any(), any());
    }

    @Test
    void login_byEmail_whenIdentifierContainsAt_looksUpByEmailAndPopulatesStudentId() {
        AppUser student = new AppUser("adm-2026-001", "s@school.edu", "$2a$12$hash", "Amina", Role.STUDENT);
        when(appUserRepository.findByEmailIgnoreCase("S@SCHOOL.EDU")).thenReturn(Optional.of(student));
        when(passwordEncoder.matches("pass1234", "$2a$12$hash")).thenReturn(true);
        when(studentLookup.findProfileId(Role.STUDENT, "adm-2026-001")).thenReturn(Optional.of(42L));
        when(jwtService.generateAccessToken(student)).thenReturn("student-jwt");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        TokenResponse response = authService.login(new LoginRequest("S@SCHOOL.EDU", "pass1234"));

        assertThat(response.user().studentId()).isEqualTo(42L);
        assertThat(response.user().teacherId()).isNull();
        verify(appUserRepository, never()).findByUsernameIgnoreCase(anyString());
    }

    @Test
    void login_unknownUser_stillRunsBcryptCheckAndAuditsFailure() {
        when(appUserRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verify(passwordEncoder).matches(eq("wrong"), anyString());
        verify(auditService).recordAs("ghost", "LOGIN_FAILED", "AppUser", null, "User not found");
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void login_wrongPassword_auditsFailureAndThrowsInvalidCredentials() {
        AppUser user = new AppUser("teacher1", "t@school.edu", "$2a$12$hash", "Ayesha", Role.TEACHER);
        when(appUserRepository.findByUsernameIgnoreCase("teacher1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("teacher1", "wrongpass")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");

        verify(auditService).recordAs("teacher1", "LOGIN_FAILED", "AppUser", null, "Invalid password");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void login_disabledUserWithWrongPassword_returnsGenericInvalidCredentials() {
        AppUser disabled = new AppUser("olduser", "old@school.edu", "$2a$12$hash", "Old", Role.TEACHER);
        disabled.setEnabled(false);
        when(appUserRepository.findByUsernameIgnoreCase("olduser")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("wrong", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("olduser", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void login_disabledUserWithCorrectPassword_returnsDisabledMessageAndAuditsFailure() {
        AppUser disabled = new AppUser("olduser", "old@school.edu", "$2a$12$hash", "Old", Role.TEACHER);
        disabled.setEnabled(false);
        when(appUserRepository.findByUsernameIgnoreCase("olduser")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("correct", "$2a$12$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("olduser", "correct")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("This account is disabled. Contact the administrator.");

        verify(auditService).recordAs("olduser", "LOGIN_FAILED", "AppUser", null, "Account disabled");
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void currentUser_whenTeacherWithProfileLookup_returnsTeacherId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t-1001", "n/a", List.of())
        );
        AppUser teacher = new AppUser("t-1001", "ayesha@school.edu", "h", "Ayesha Rahman", Role.TEACHER);
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
        AppUser disabled = new AppUser("t-1001", "ayesha@school.edu", "h", "Ayesha Rahman", Role.TEACHER);
        disabled.setEnabled(false);
        when(appUserRepository.findByUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.currentUser())
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("This account is disabled. Contact the administrator.");
    }
}
