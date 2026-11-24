package com.school.auth;

import com.school.user.AppUser;
import com.school.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 120) String username,
            @NotBlank @Size(max = 100) String password
    ) {
    }

    public record CurrentUser(
            Long id,
            String username,
            String fullName,
            String email,
            Role role,
            boolean mustChangePassword,
            Long studentId,
            Long teacherId
    ) {
        public static CurrentUser from(AppUser user, Long studentId, Long teacherId) {
            return new CurrentUser(
                    user.getId(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getRole(),
                    user.isMustChangePassword(),
                    studentId,
                    teacherId
            );
        }
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            CurrentUser user
    ) {
        public static TokenResponse from(String accessToken, long expiresIn, CurrentUser user) {
            return new TokenResponse(accessToken, "Bearer", expiresIn, user);
        }
    }
}
