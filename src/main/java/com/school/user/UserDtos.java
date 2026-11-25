package com.school.user;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            Long id,
            String username,
            String email,
            String fullName,
            Role role,
            boolean enabled,
            boolean locked,
            boolean mustChangePassword,
            Instant lastLoginAt
    ) {
        public static UserResponse from(AppUser user, Instant now) {
            boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole(),
                    user.isEnabled(),
                    locked,
                    user.isMustChangePassword(),
                    user.getLastLoginAt()
            );
        }
    }

    public record StatusRequest(
            @NotNull Boolean enabled
    ) {
    }

    public record TemporaryPasswordResponse(
            String username,
            String temporaryPassword
    ) {
        public static TemporaryPasswordResponse from(String username, String temporaryPassword) {
            return new TemporaryPasswordResponse(username, temporaryPassword);
        }

        public static TemporaryPasswordResponse from(UserService.NewAccount account) {
            return new TemporaryPasswordResponse(account.user().getUsername(), account.temporaryPassword());
        }
    }
}
