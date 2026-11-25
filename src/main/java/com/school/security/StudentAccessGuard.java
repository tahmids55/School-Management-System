package com.school.security;

import com.school.common.SecurityUtils;
import com.school.common.exception.ForbiddenException;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Ownership rules for student data, used by services AFTER @PreAuthorize has checked the role.
 * Deny by default: anything not explicitly allowed below gets 403.
 * <ul>
 *   <li>ADMIN: every student.</li>
 *   <li>STUDENT: only their own record.</li>
 *   <li>TEACHER: only students whose class is in TeachingScope (F16). No classes = nobody.</li>
 * </ul>
 */
@Component
public class StudentAccessGuard {

    public static final String NO_PROFILE = "No student profile is linked to this account";
    public static final String OWN_RECORDS_ONLY = "You can only view your own records";
    public static final String OWN_CLASSES_ONLY = "You can only view students of your own classes";

    private final StudentRepository studentRepository;
    private final TeachingScope teachingScope;

    public StudentAccessGuard(StudentRepository studentRepository, TeachingScope teachingScope) {
        this.studentRepository = studentRepository;
        this.teachingScope = teachingScope;
    }

    /** The student record of the logged-in STUDENT (with its class), or 403. */
    @Transactional(readOnly = true)
    public Student currentStudent() {
        return SecurityUtils.currentUsername()
                .flatMap(studentRepository::findByUsername)
                .orElseThrow(() -> new ForbiddenException(NO_PROFILE));
    }

    @Transactional(readOnly = true)
    public void checkCanViewStudent(Long studentId) {
        if (SecurityUtils.hasRole("ADMIN")) {
            return;
        }
        if (SecurityUtils.hasRole("STUDENT")) {
            if (!currentStudent().getId().equals(studentId)) {
                throw new ForbiddenException(OWN_RECORDS_ONLY);
            }
            return;
        }
        if (SecurityUtils.hasRole("TEACHER")) {
            // A missing student or a student without a class is not in any teacher's classes: deny.
            Optional<Long> classId = studentRepository.findClassIdById(studentId);
            if (classId.isPresent() && teacherClassIds().contains(classId.get())) {
                return;
            }
            throw new ForbiddenException(OWN_CLASSES_ONLY);
        }
        throw new ForbiddenException(OWN_RECORDS_ONLY);
    }

    /** May the caller see the roster of this class? ADMIN yes, TEACHER only own classes, others no. */
    public void checkCanViewClass(Long classId) {
        if (SecurityUtils.hasRole("ADMIN")) {
            return;
        }
        if (SecurityUtils.hasRole("TEACHER") && teacherClassIds().contains(classId)) {
            return;
        }
        throw new ForbiddenException(OWN_CLASSES_ONLY);
    }

    /** Classes the logged-in teacher may see (empty for everyone else). */
    public Set<Long> teacherClassIds() {
        Set<Long> ids = SecurityUtils.currentUsername().map(teachingScope::accessibleClassIds).orElse(null);
        return ids == null ? Set.of() : ids;
    }
}
