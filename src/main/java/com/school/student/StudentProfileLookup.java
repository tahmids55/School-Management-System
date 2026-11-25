package com.school.student;

import com.school.security.ProfileLookup;
import com.school.user.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Tells AuthService (/auth/me) which student record belongs to a STUDENT login. */
@Component
public class StudentProfileLookup implements ProfileLookup {

    private final StudentRepository studentRepository;

    public StudentProfileLookup(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findProfileId(Role role, String username) {
        if (role != Role.STUDENT || username == null || username.isBlank()) {
            return Optional.empty();
        }
        return studentRepository.findIdByUsername(username.trim());
    }
}
