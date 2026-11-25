package com.school.user;

import com.school.audit.AuditService;
import com.school.auth.RefreshTokenService;
import com.school.common.PageResponse;
import com.school.common.SecurityUtils;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.user.UserDtos.TemporaryPasswordResponse;
import com.school.user.UserDtos.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Shared account lifecycle and admin user-management service.
 */
@Service
public class UserService {

    public static final String AUDIT_ACCOUNT_CREATED = "ACCOUNT_CREATED";
    public static final String AUDIT_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String AUDIT_ACCOUNT_ENABLED = "ACCOUNT_ENABLED";
    public static final String AUDIT_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String AUDIT_ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("username"));
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "username",
            "email",
            "fullName",
            "role",
            "enabled",
            "lastLoginAt",
            "createdAt"
    );

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final Clock clock;

    public UserService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            PasswordGenerator passwordGenerator,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.clock = clock;
    }

    public record NewAccount(AppUser user, String temporaryPassword) {
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, Role role, Pageable pageable) {
        Pageable effectivePageable = resolvePageable(pageable);
        String trimmedQuery = (q == null) ? null : q.trim();
        boolean hasQuery = trimmedQuery != null && !trimmedQuery.isEmpty();

        Page<AppUser> page;
        if (hasQuery && role != null) {
            page = appUserRepository.searchByRole(role, toLikePattern(trimmedQuery), effectivePageable);
        } else if (hasQuery) {
            page = appUserRepository.search(toLikePattern(trimmedQuery), effectivePageable);
        } else if (role != null) {
            page = appUserRepository.findByRole(role, effectivePageable);
        } else {
            page = appUserRepository.findAll(effectivePageable);
        }

        Instant now = clock.instant();
        return PageResponse.from(page, user -> UserResponse.from(user, now));
    }

    @Transactional
    public NewAccount createAccount(String username, String email, String fullName, Role role) {
        String normalizedUsername = normalizeLower(username, "username");
        String normalizedEmail = normalizeLower(email, "email");
        String normalizedFullName = normalizeText(fullName, "fullName");
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        if (appUserRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ConflictException("Username " + normalizedUsername + " is already taken");
        }
        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with email " + normalizedEmail + " already exists");
        }

        String temporaryPassword = passwordGenerator.generate();
        AppUser user = new AppUser(
                normalizedUsername,
                normalizedEmail,
                passwordEncoder.encode(temporaryPassword),
                normalizedFullName,
                role
        );
        user.setMustChangePassword(true);

        AppUser saved = appUserRepository.save(user);
        auditService.record(
                AUDIT_ACCOUNT_CREATED,
                "AppUser",
                entityId(saved),
                "Created " + role + " account for " + normalizedUsername
        );
        return new NewAccount(saved, temporaryPassword);
    }

    @Transactional
    public UserResponse updateStatus(Long id, boolean enabled) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " was not found"));

        if (!enabled) {
            if (isCurrentUser(user)) {
                throw new BusinessRuleException("You cannot disable your own account");
            }
            disable(user);
            auditService.record(
                    AUDIT_ACCOUNT_DISABLED,
                    "AppUser",
                    entityId(user),
                    "Disabled account " + user.getUsername()
            );
        } else {
            user.setEnabled(true);
            appUserRepository.save(user);
            auditService.record(
                    AUDIT_ACCOUNT_ENABLED,
                    "AppUser",
                    entityId(user),
                    "Enabled account " + user.getUsername()
            );
        }

        return UserResponse.from(user, clock.instant());
    }

    @Transactional
    public UserResponse unlock(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " was not found"));

        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        appUserRepository.save(user);

        auditService.record(
                AUDIT_ACCOUNT_UNLOCKED,
                "AppUser",
                entityId(user),
                "Unlocked account " + user.getUsername()
        );
        return UserResponse.from(user, clock.instant());
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " was not found"));

        String temporaryPassword = passwordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(user);

        auditService.record(
                AUDIT_PASSWORD_RESET,
                "AppUser",
                entityId(user),
                "Password reset for " + user.getUsername()
        );
        return TemporaryPasswordResponse.from(user.getUsername(), temporaryPassword);
    }

    @Transactional
    public void disable(AppUser user) {
        if (user == null) {
            return;
        }
        user.setEnabled(false);
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
    }

    private static Pageable resolvePageable(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Invalid sort field: " + order.getProperty());
            }
        }
        return pageable;
    }

    private static boolean isCurrentUser(AppUser user) {
        if (SecurityUtils.currentUserId().filter(id -> id.equals(user.getId())).isPresent()) {
            return true;
        }
        return SecurityUtils.currentUsername()
                .filter(name -> name.equalsIgnoreCase(user.getUsername()))
                .isPresent();
    }

    private static String toLikePattern(String query) {
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static String normalizeLower(String value, String fieldName) {
        return normalizeText(value, fieldName).toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String entityId(AppUser user) {
        return user.getId() != null ? user.getId().toString() : null;
    }
}
