package com.school.common;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Helpers for reading the authenticated user from Spring Security's context.
 */
public final class SecurityUtils {

    public static final String SYSTEM_ACTOR = "system";

    private SecurityUtils() {
    }

    public static Optional<String> currentUsername() {
        Authentication authentication = currentAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    public static String currentUsernameOrSystem() {
        return currentUsername().orElse(SYSTEM_ACTOR);
    }

    public static Optional<Long> currentUserId() {
        Authentication authentication = currentAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        Object claim = jwt.getClaim("uid");
        if (claim instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (claim instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        Authentication authentication = currentAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private static Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication;
    }
}
