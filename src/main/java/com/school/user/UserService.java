package com.school.user;

import com.school.audit.AuditService;
import com.school.auth.RefreshTokenService;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.user.UserDtos.TemporaryPasswordResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Shared account lifecycle API for account creation, temporary password reset, and account disabling.
 */
@Service
public class UserService {

    public static final String AUDIT_ACCOUNT_CREATED = "ACCOUNT_CREATED";
    public static final String AUDIT_PASSWORD_RESET = "PASSWORD_RESET";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public UserService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            PasswordGenerator passwordGenerator,
            RefreshTokenService refreshTokenService,
            AuditService auditService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    public record NewAccount(AppUser user, String temporaryPassword) {
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
