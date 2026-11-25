package com.school.teacher;

import com.school.security.ProfileLookup;
import com.school.user.Role;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the linked {@link Teacher} profile ID for {@link Role#TEACHER} logins.
 */
@Component
public class TeacherProfileLookup implements ProfileLookup {

    private final TeacherRepository teacherRepository;

    public TeacherProfileLookup(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    @Override
    public Optional<Long> findProfileId(Role role, String username) {
        if (role != Role.TEACHER || username == null || username.isBlank()) {
            return Optional.empty();
        }
        return teacherRepository.findIdByUserUsernameIgnoreCase(username.trim());
    }
}
