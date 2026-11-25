package com.school.security;

import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClassRepository;
import com.school.teacher.TeacherRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the class IDs accessible to a teacher: classes where they are class teacher or teach a subject.
 */
@Component
public class TeacherTeachingScope implements TeachingScope {

    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectRepository classSubjectRepository;

    public TeacherTeachingScope(
            TeacherRepository teacherRepository,
            SchoolClassRepository schoolClassRepository,
            ClassSubjectRepository classSubjectRepository
    ) {
        this.teacherRepository = teacherRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSubjectRepository = classSubjectRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> accessibleClassIds(String teacherUsername) {
        if (teacherUsername == null || teacherUsername.isBlank()) {
            return Set.of();
        }
        Optional<Long> teacherId = teacherRepository.findIdByUserUsernameIgnoreCase(teacherUsername.trim());
        if (teacherId.isEmpty()) {
            return Set.of();
        }
        Set<Long> classIds = new LinkedHashSet<>();
        classIds.addAll(schoolClassRepository.findIdsByClassTeacherId(teacherId.get()));
        classIds.addAll(classSubjectRepository.findClassIdsByTeacherId(teacherId.get()));
        return Set.copyOf(classIds);
    }
}
