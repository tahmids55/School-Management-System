package com.school.security;

import com.school.user.Role;

import java.util.Optional;

/**
 * Extension contract allowing Student and Teacher modules to expose linked profile IDs
 * without editing {@code AuthService}. Returns empty when no implementation exists.
 */
public interface ProfileLookup {

    Optional<Long> findProfileId(Role role, String username);
}
