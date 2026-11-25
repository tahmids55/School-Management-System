package com.school.academic;

import com.school.academic.AcademicDtos.ClassSubjectRequest;
import com.school.academic.AcademicDtos.ClassSubjectResponse;
import com.school.audit.AuditService;
import com.school.common.exception.NotFoundException;
import com.school.security.AccessGuard;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Subject and teacher assignments for a class, ordered by subject code.
 */
@Service
public class ClassSubjectService {

    public static final String ENTITY_TYPE = "ClassSubject";
    public static final String AUDIT_SUBJECT_ASSIGNED = "SUBJECT_ASSIGNED";

    private final ClassSubjectRepository classSubjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final AccessGuard accessGuard;
    private final AuditService auditService;

    public ClassSubjectService(
            ClassSubjectRepository classSubjectRepository,
            SchoolClassRepository schoolClassRepository,
            SubjectRepository subjectRepository,
            TeacherRepository teacherRepository,
            AccessGuard accessGuard,
            AuditService auditService
    ) {
        this.classSubjectRepository = classSubjectRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ClassSubjectResponse> listForClass(Long classId) {
        accessGuard.checkCanManageClass(classId);
        return classSubjectRepository.findAllByClassIdWithDetails(classId).stream()
                .map(ClassSubjectResponse::from)
                .toList();
    }

    @Transactional
    public ClassSubjectResponse assign(Long classId, ClassSubjectRequest request) {
        SchoolClass schoolClass = schoolClassRepository.findByIdWithYear(classId)
                .orElseThrow(() -> new NotFoundException("Class", classId));
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new NotFoundException("Subject", request.subjectId()));
        Teacher teacher = request.teacherId() == null
                ? null
                : teacherRepository.findById(request.teacherId())
                        .orElseThrow(() -> new NotFoundException("Teacher", request.teacherId()));

        ClassSubject assignment = classSubjectRepository
                .findBySchoolClassIdAndSubjectIdWithDetails(classId, request.subjectId())
                .orElseGet(() -> new ClassSubject(schoolClass, subject, teacher));
        assignment.setTeacher(teacher);

        ClassSubject saved = classSubjectRepository.saveAndFlush(assignment);
        String detail = "Assigned " + subject.getCode() + " in " + schoolClass.getDisplayName()
                + (teacher != null ? " to " + teacher.getFullName() : " (unassigned)");
        auditService.record(AUDIT_SUBJECT_ASSIGNED, ENTITY_TYPE, String.valueOf(saved.getId()), detail);
        return ClassSubjectResponse.from(saved);
    }

    @Transactional
    public void remove(Long classId, Long subjectId) {
        if (!schoolClassRepository.existsById(classId)) {
            throw new NotFoundException("Class", classId);
        }
        ClassSubject assignment = classSubjectRepository
                .findBySchoolClassIdAndSubjectIdWithDetails(classId, subjectId)
                .orElseThrow(() -> new NotFoundException("This subject is not assigned to the class"));
        classSubjectRepository.delete(assignment);
        classSubjectRepository.flush();
    }
}
