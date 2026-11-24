package com.school.user;

public final class UserDtos {

    private UserDtos() {
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
