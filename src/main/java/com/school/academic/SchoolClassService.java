package com.school.academic;

import com.school.academic.AcademicDtos.ClassRequest;
import com.school.academic.AcademicDtos.ClassResponse;
import com.school.audit.AuditService;
import com.school.common.SecurityUtils;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.security.AccessGuard;
import com.school.security.TeachingScope;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Classes inside an academic year: name trimmed, section trimmed upper-case,
 * name + section unique per year, ordered by name then section.
 */
@Service
public class SchoolClassService {

    static final String ENTITY_TYPE = "SchoolClass";

    private final SchoolClassRepository schoolClassRepository;
    private final AcademicYearRepository academicYearRepository;
    private final TeacherRepository teacherRepository;
    private final AccessGuard accessGuard;
    private final TeachingScope teachingScope;
    private final AuditService auditService;

    public SchoolClassService(
            SchoolClassRepository schoolClassRepository,
            AcademicYearRepository academicYearRepository,
            TeacherRepository teacherRepository,
            AccessGuard accessGuard,
            TeachingScope teachingScope,
            AuditService auditService
    ) {
        this.schoolClassRepository = schoolClassRepository;
        this.academicYearRepository = academicYearRepository;
        this.teacherRepository = teacherRepository;
        this.accessGuard = accessGuard;
        this.teachingScope = teachingScope;
        this.auditService = auditService;
    }

    /**
     * Classes of the chosen year, or of the current year when none is chosen.
     * Admins see all classes; teachers see only classes they teach or lead.
     */
    @Transactional(readOnly = true)
    public List<ClassResponse> list(Long academicYearId) {
        Optional<AcademicYear> year = academicYearId == null
                ? academicYearRepository.findByCurrentTrue()
                : Optional.of(findYear(academicYearId));
        if (year.isEmpty()) {
            return List.of();
        }
        List<SchoolClass> classes;
        if (accessGuard.isAdmin()) {
            classes = schoolClassRepository.findAllByYearWithYear(year.get().getId());
        } else {
            String username = SecurityUtils.currentUsername().orElse(null);
            Set<Long> allowedIds = teachingScope.accessibleClassIds(username);
            if (allowedIds.isEmpty()) {
                return List.of();
            }
            classes = schoolClassRepository.findAllByYearAndIdsWithYear(year.get().getId(), allowedIds);
        }
        if (classes.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = studentCounts(classes);
        return classes.stream()
                .map(c -> ClassResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse get(Long id) {
        SchoolClass schoolClass = findClass(id);
        accessGuard.checkCanManageClass(id);
        return ClassResponse.from(schoolClass, schoolClassRepository.countStudents(id));
    }

    @Transactional
    public ClassResponse create(ClassRequest request) {
        AcademicYear year = findYear(request.academicYearId());
        String name = request.name().trim();
        String section = normalizeSection(request.section());
        if (schoolClassRepository.existsByAcademicYearIdAndNameIgnoreCaseAndSection(year.getId(), name, section)) {
            throw new ConflictException(name + " - " + section + " already exists in " + year.getName());
        }
        Teacher classTeacher = resolveClassTeacher(request.classTeacherId());
        SchoolClass saved = schoolClassRepository.save(
                new SchoolClass(year, name, section, normalizeRoom(request.room()), request.capacity(), classTeacher));
        auditService.record("CLASS_CREATED", ENTITY_TYPE, String.valueOf(saved.getId()),
                "Created " + saved.getDisplayName() + " in " + year.getName());
        return ClassResponse.from(saved, 0);
    }

    @Transactional
    public ClassResponse update(Long id, ClassRequest request) {
        SchoolClass schoolClass = findClass(id);
        AcademicYear year = schoolClass.getAcademicYear().getId().equals(request.academicYearId())
                ? schoolClass.getAcademicYear()
                : findYear(request.academicYearId());
        String name = request.name().trim();
        String section = normalizeSection(request.section());
        if (schoolClassRepository.existsByAcademicYearIdAndNameIgnoreCaseAndSectionAndIdNot(
                year.getId(), name, section, id)) {
            throw new ConflictException(name + " - " + section + " already exists in this academic year");
        }
        long enrolled = schoolClassRepository.countStudents(id);
        if (request.capacity() < enrolled) {
            throw new ConflictException("Capacity cannot be lower than the " + enrolled + " students already enrolled");
        }
        Teacher classTeacher = resolveClassTeacher(request.classTeacherId());
        schoolClass.setAcademicYear(year);
        schoolClass.setName(name);
        schoolClass.setSection(section);
        schoolClass.setRoom(normalizeRoom(request.room()));
        schoolClass.setCapacity(request.capacity());
        schoolClass.setClassTeacher(classTeacher);
        return ClassResponse.from(schoolClassRepository.saveAndFlush(schoolClass), enrolled);
    }

    /**
     * A class with active students cannot be deleted. Flushing turns any other foreign-key use
     * (for example graduated students who keep it as history) into a 409.
     */
    @Transactional
    public void delete(Long id) {
        SchoolClass schoolClass = findClass(id);
        if (schoolClassRepository.countStudents(id) > 0) {
            throw new ConflictException("Move the students out of " + schoolClass.getDisplayName() + " before deleting it");
        }
        schoolClassRepository.delete(schoolClass);
        schoolClassRepository.flush();
        auditService.record("CLASS_DELETED", ENTITY_TYPE, String.valueOf(id),
                "Deleted " + schoolClass.getDisplayName() + " from " + schoolClass.getAcademicYear().getName());
    }

    private Teacher resolveClassTeacher(Long classTeacherId) {
        if (classTeacherId == null) {
            return null;
        }
        return teacherRepository.findById(classTeacherId)
                .orElseThrow(() -> new NotFoundException("Teacher", classTeacherId));
    }

    private Map<Long, Long> studentCounts(List<SchoolClass> classes) {
        List<Long> ids = classes.stream().map(SchoolClass::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : schoolClassRepository.countStudentsByClassIds(ids)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private SchoolClass findClass(Long id) {
        return schoolClassRepository.findByIdWithYear(id)
                .orElseThrow(() -> new NotFoundException("Class", id));
    }

    private AcademicYear findYear(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Academic year", id));
    }

    static String normalizeSection(String section) {
        return section.trim().toUpperCase(Locale.ROOT);
    }

    /** Blank room means "no room". */
    static String normalizeRoom(String room) {
        return room == null || room.isBlank() ? null : room.trim();
    }
}
