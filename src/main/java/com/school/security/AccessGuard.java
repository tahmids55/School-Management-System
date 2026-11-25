package com.school.security;

import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClassRepository;
import com.school.common.SecurityUtils;
import com.school.common.exception.NotFoundException;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ownership and role guard for teacher-scoped operations (class management and subject grading).
 */
@Component
public class AccessGuard {

    public static final String NO_TEACHER_PROFILE_MESSAGE = "No teacher profile is linked to this account";
    public static final String NOT_TEACHING_CLASS_MESSAGE = "You do not teach in this class";
    public static final String SUBJECT_NOT_ASSIGNED_TO_TEACHER_MESSAGE =
            "This subject is not assigned to you in this class";

    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectRepository classSubjectRepository;

    public AccessGuard(
            TeacherRepository teacherRepository,
            SchoolClassRepository schoolClassRepository,
            ClassSubjectRepository classSubjectRepository
    ) {
        this.teacherRepository = teacherRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSubjectRepository = classSubjectRepository;
    }

    public boolean isAdmin() {
        return SecurityUtils.hasRole("ADMIN");
    }

    @Transactional(readOnly = true)
    public Teacher currentTeacher() {
        String username = SecurityUtils.currentUsername()
                .orElseThrow(() -> new AccessDeniedException(NO_TEACHER_PROFILE_MESSAGE));
        return teacherRepository.findByUserUsernameIgnoreCase(username)
                .orElseThrow(() -> new AccessDeniedException(NO_TEACHER_PROFILE_MESSAGE));
    }

    /**
     * Allows admins, the class teacher of {@code classId}, or any teacher assigned to a subject in {@code classId}.
     */
    @Transactional(readOnly = true)
    public void checkCanManageClass(Long classId) {
        if (!schoolClassRepository.existsById(classId)) {
            throw new NotFoundException("Class", classId);
        }
        if (isAdmin()) {
            return;
        }
        Teacher teacher = currentTeacher();
        boolean allowed = schoolClassRepository.existsByIdAndClassTeacherId(classId, teacher.getId())
                || classSubjectRepository.existsBySchoolClassIdAndTeacherId(classId, teacher.getId());
        if (!allowed) {
            throw new AccessDeniedException(NOT_TEACHING_CLASS_MESSAGE);
        }
    }

    /**
     * Allows admins or the exact teacher assigned to {@code subjectId} in {@code classId}.
     * Being the class teacher alone does not grant subject grading access.
     */
    @Transactional(readOnly = true)
    public void checkCanGradeSubject(Long classId, Long subjectId) {
        if (!schoolClassRepository.existsById(classId)) {
            throw new NotFoundException("Class", classId);
        }
        if (isAdmin()) {
            return;
        }
        Teacher teacher = currentTeacher();
        if (!classSubjectRepository.existsBySchoolClassIdAndSubjectIdAndTeacherId(
                classId, subjectId, teacher.getId())) {
            throw new AccessDeniedException(SUBJECT_NOT_ASSIGNED_TO_TEACHER_MESSAGE);
        }
    }
}
