package com.school.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.auth.AuthService;
import com.school.auth.RefreshTokenService;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Reloads the authenticated account on every {@code /api/**} request to enforce disabled status
 * and temporary-password change requirements server-side.
 */
public class AccountStateFilter extends OncePerRequestFilter {

    public static final String MUST_CHANGE_PASSWORD_MESSAGE = "Change your temporary password first";

    private static final Set<String> ALLOWED_WHEN_MUST_CHANGE_PASSWORD = Set.of(
            "/api/v1/auth/me",
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/refresh"
    );

    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;

    public AccountStateFilter(AppUserRepository appUserRepository, ObjectMapper objectMapper) {
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<AppUser> maybeUser = appUserRepository.findByUsernameIgnoreCase(authentication.getName());
        if (maybeUser.isEmpty()) {
            SecurityContextHolder.clearContext();
            SecurityProblemWriter.write(
                    objectMapper,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    RefreshTokenService.SESSION_EXPIRED_MESSAGE
            );
            return;
        }

        AppUser user = maybeUser.get();
        if (!user.isEnabled()) {
            SecurityContextHolder.clearContext();
            SecurityProblemWriter.write(
                    objectMapper,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    AuthService.ACCOUNT_DISABLED_MESSAGE
            );
            return;
        }

        if (user.isMustChangePassword() && !ALLOWED_WHEN_MUST_CHANGE_PASSWORD.contains(path)) {
            SecurityProblemWriter.write(
                    objectMapper,
                    response,
                    HttpStatus.FORBIDDEN,
                    MUST_CHANGE_PASSWORD_MESSAGE
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
