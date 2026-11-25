package com.school.user;

import com.school.audit.AuditService;
import com.school.auth.RefreshTokenService;
import com.school.common.PageResponse;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.user.UserDtos.TemporaryPasswordResponse;
import com.school.user.UserDtos.UserResponse;
import com.school.user.UserService.NewAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordGenerator passwordGenerator;

    @Mock
    private RefreshTokenService refreshTokenService;

    private static final Instant FIXED_NOW = Instant.parse("2026-11-25T10:00:00Z");

    @Mock
    private AuditService auditService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneId.of("Asia/Dhaka"));
        userService = new UserService(
                appUserRepository,
                passwordEncoder,
                passwordGenerator,
                refreshTokenService,
                auditService,
                clock
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAccount_normalizesLowerCase_setsMustChangePassword_andAudits() {
        when(appUserRepository.existsByUsernameIgnoreCase("t-1002")).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCase("teacher@school.edu")).thenReturn(false);
        when(passwordGenerator.generate()).thenReturn("AbCdEf234567");
        when(passwordEncoder.encode("AbCdEf234567")).thenReturn("$2a$12$encoded");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 15L);
            return saved;
        });

        NewAccount created = userService.createAccount(
                "  T-1002 ",
                " Teacher@School.EDU ",
                "  Karim Uddin ",
                Role.TEACHER
        );

        assertThat(created.temporaryPassword()).isEqualTo("AbCdEf234567");
        assertThat(created.user().getId()).isEqualTo(15L);
        assertThat(created.user().getUsername()).isEqualTo("t-1002");
        assertThat(created.user().getEmail()).isEqualTo("teacher@school.edu");
        assertThat(created.user().getFullName()).isEqualTo("Karim Uddin");
        assertThat(created.user().getRole()).isEqualTo(Role.TEACHER);
        assertThat(created.user().isEnabled()).isTrue();
        assertThat(created.user().isMustChangePassword()).isTrue();
        assertThat(created.user().getPasswordHash()).isEqualTo("$2a$12$encoded");
        verify(auditService).record(eq("ACCOUNT_CREATED"), eq("AppUser"), eq("15"), contains("t-1002"));
    }

    @Test
    void createAccount_duplicateUsername_throws409WithExactMessage() {
        when(appUserRepository.existsByUsernameIgnoreCase("admin")).thenReturn(true);

        assertThatThrownBy(() -> userService.createAccount("ADMIN", "new@school.edu", "Name", Role.ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username admin is already taken");

        verify(appUserRepository, never()).save(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    void createAccount_duplicateEmail_throws409WithExactMessage() {
        when(appUserRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCase("admin@school.edu")).thenReturn(true);

        assertThatThrownBy(() -> userService.createAccount("newuser", "ADMIN@school.edu", "Name", Role.TEACHER))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An account with email admin@school.edu already exists");

        verify(appUserRepository, never()).save(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    void resetPassword_updatesHashSetsFlagRevokesSessionsAndAudits() {
        AppUser user = new AppUser("t-1001", "t@school.edu", "$2a$12$old", "Ayesha", Role.TEACHER);
        ReflectionTestUtils.setField(user, "id", 8L);
        when(appUserRepository.findById(8L)).thenReturn(Optional.of(user));
        when(passwordGenerator.generate()).thenReturn("XyZ23456789a");
        when(passwordEncoder.encode("XyZ23456789a")).thenReturn("$2a$12$new");

        TemporaryPasswordResponse response = userService.resetPassword(8L);

        assertThat(response.username()).isEqualTo("t-1001");
        assertThat(response.temporaryPassword()).isEqualTo("XyZ23456789a");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$new");
        assertThat(user.isMustChangePassword()).isTrue();
        verify(appUserRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(user);
        verify(auditService).record(eq("PASSWORD_RESET"), eq("AppUser"), eq("8"), contains("t-1001"));
    }

    @Test
    void resetPassword_missingUser_throwsNotFound() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resetPassword(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 99 was not found");

        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void disable_disablesUserAndRevokesAllSessions() {
        AppUser user = new AppUser("s-1", "s@school.edu", "$2a$12$hash", "Student", Role.STUDENT);
        ReflectionTestUtils.setField(user, "id", 21L);

        userService.disable(user);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void disable_nullUser_isSafeNoOp() {
        userService.disable(null);

        verifyNoInteractions(appUserRepository, refreshTokenService, auditService);
    }

    @Test
    void search_escapesWildcards_computesLockedFromClock_andRejectsUnknownSort() {
        AppUser lockedUser = new AppUser("t-1001", "ayesha@school.edu", "$2a$12$h", "Ayesha", Role.TEACHER);
        ReflectionTestUtils.setField(lockedUser, "id", 2L);
        lockedUser.setLockedUntil(FIXED_NOW.plusSeconds(600));

        when(appUserRepository.searchByRole(eq(Role.TEACHER), eq("%50!% !_test%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lockedUser)));

        PageResponse<UserResponse> response = userService.search(" 50% _Test ", Role.TEACHER, PageRequest.of(0, 20));

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(2L);
            assertThat(item.locked()).isTrue();
            assertThat(item.enabled()).isTrue();
        });

        assertThatThrownBy(() -> userService.search(null, null, PageRequest.of(0, 20, Sort.by("passwordHash"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort field");
    }

    @Test
    void updateStatus_selfDisableThrows422_otherDisableRevokesAndAudits_enableAudits() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a", List.of())
        );
        AppUser admin = new AppUser("admin", "admin@school.edu", "$2a$12$h", "Nasima", Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 1L);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateStatus(1L, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You cannot disable your own account");
        verify(refreshTokenService, never()).revokeAllForUser(any());

        AppUser teacher = new AppUser("t-1001", "t@school.edu", "$2a$12$h", "Ayesha", Role.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 2L);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(teacher));

        UserResponse disabled = userService.updateStatus(2L, false);
        assertThat(disabled.enabled()).isFalse();
        verify(refreshTokenService).revokeAllForUser(teacher);
        verify(auditService).record(eq(UserService.AUDIT_ACCOUNT_DISABLED), eq("AppUser"), eq("2"), contains("t-1001"));

        UserResponse reenabled = userService.updateStatus(2L, true);
        assertThat(reenabled.enabled()).isTrue();
        verify(auditService).record(eq(UserService.AUDIT_ACCOUNT_ENABLED), eq("AppUser"), eq("2"), contains("t-1001"));
    }

    @Test
    void unlock_clearsLockTimeAndFailedCounter_andAudits() {
        AppUser student = new AppUser("s-1", "s@school.edu", "$2a$12$h", "Amina", Role.STUDENT);
        ReflectionTestUtils.setField(student, "id", 3L);
        student.setLockedUntil(FIXED_NOW.plusSeconds(300));
        student.setFailedLoginAttempts(5);
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(student));

        UserResponse unlocked = userService.unlock(3L);

        assertThat(unlocked.locked()).isFalse();
        assertThat(student.getLockedUntil()).isNull();
        assertThat(student.getFailedLoginAttempts()).isZero();
        verify(appUserRepository).save(student);
        verify(auditService).record(eq(UserService.AUDIT_ACCOUNT_UNLOCKED), eq("AppUser"), eq("3"), contains("s-1"));
    }
}
