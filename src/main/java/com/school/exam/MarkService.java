package com.school.exam;

import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.NotFoundException;
import com.school.exam.ExamDtos.MarkEntry;
import com.school.exam.ExamDtos.MarkRow;
import com.school.exam.ExamDtos.MarkSheet;
import com.school.exam.ExamDtos.MarkSheetRequest;
import com.school.security.AccessGuard;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarkService {

    public static final String AUDIT_MARKS_SAVED = "MARKS_SAVED";

    private final ExamRepository examRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final StudentRepository studentRepository;
    private final MarkRepository markRepository;
    private final AccessGuard accessGuard;
    private final AuditService auditService;

    public MarkService(
            ExamRepository examRepository,
            SchoolClassRepository schoolClassRepository,
            ClassSubjectRepository classSubjectRepository,
            StudentRepository studentRepository,
            MarkRepository markRepository,
            AccessGuard accessGuard,
            AuditService auditService
    ) {
        this.examRepository = examRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.studentRepository = studentRepository;
        this.markRepository = markRepository;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public MarkSheet getSheet(Long examId, Long classId, Long subjectId) {
        SheetContext context = loadContext(examId, classId, subjectId);
        List<Student> roster = studentRepository.findActiveRoster(classId);
        Map<Long, Mark> marksByStudent = loadMarksByStudent(examId, subjectId, roster);
        return toMarkSheet(context, roster, marksByStudent);
    }

    @Transactional
    public MarkSheet saveSheet(Long examId, MarkSheetRequest request) {
        SheetContext context = loadContext(examId, request.classId(), request.subjectId());
        context.exam().requireDraft();

        List<Student> roster = studentRepository.findActiveRoster(request.classId());
        Map<Long, Student> rosterById = roster.stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        // Phase 1: Validate every row before touching the database.
        Set<Long> seenStudentIds = new HashSet<>();
        List<ValidatedRow> validatedRows = new ArrayList<>(request.entries().size());
        for (MarkEntry entry : request.entries()) {
            if (!seenStudentIds.add(entry.studentId())) {
                throw new BusinessRuleException("Duplicate student " + entry.studentId() + " in mark sheet");
            }
            Student student = rosterById.get(entry.studentId());
            if (student == null) {
                throw new BusinessRuleException(
                        "Student " + entry.studentId() + " is not an active student of this class"
                );
            }
            BigDecimal validatedMarks = MarkValidator.validateEntry(entry, context.subject());
            validatedRows.add(new ValidatedRow(student, validatedMarks, entry.absent()));
        }

        // Phase 2: Save all validated rows in one transaction.
        Map<Long, Mark> existingByStudent = loadMarksByStudent(examId, request.subjectId(), roster);
        List<Mark> toSave = new ArrayList<>(validatedRows.size());
        for (ValidatedRow row : validatedRows) {
            Mark existing = existingByStudent.get(row.student().getId());
            if (existing != null) {
                existing.update(row.marksObtained(), row.absent());
                toSave.add(existing);
            } else {
                Mark created = new Mark(
                        context.exam(),
                        row.student(),
                        context.subject(),
                        row.marksObtained(),
                        row.absent()
                );
                toSave.add(created);
                existingByStudent.put(row.student().getId(), created);
            }
        }
        markRepository.saveAllAndFlush(toSave);

        auditService.record(
                AUDIT_MARKS_SAVED,
                "Exam",
                String.valueOf(context.exam().getId()),
                "Saved " + toSave.size() + " marks for "
                        + context.schoolClass().getDisplayName() + " / " + context.subject().getCode()
        );

        return toMarkSheet(context, roster, existingByStudent);
    }

    private SheetContext loadContext(Long examId, Long classId, Long subjectId) {
        Exam exam = examRepository.findByIdWithAcademicYear(examId)
                .orElseThrow(() -> new NotFoundException("Exam", examId));
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new NotFoundException("Class", classId));

        if (!schoolClass.getAcademicYear().getId().equals(exam.getAcademicYear().getId())) {
            throw new BusinessRuleException(
                    "Class " + schoolClass.getDisplayName()
                            + " does not belong to academic year " + exam.getAcademicYear().getName()
            );
        }

        ClassSubject classSubject = classSubjectRepository
                .findBySchoolClassIdAndSubjectIdWithDetails(classId, subjectId)
                .orElseThrow(() -> new NotFoundException("This subject is not taught in the selected class"));

        accessGuard.checkCanGradeSubject(classId, subjectId);
        return new SheetContext(exam, schoolClass, classSubject.getSubject());
    }

    private Map<Long, Mark> loadMarksByStudent(Long examId, Long subjectId, List<Student> roster) {
        if (roster.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<Long> studentIds = roster.stream().map(Student::getId).toList();
        return markRepository.findByExamIdAndSubjectIdAndStudentIdIn(examId, subjectId, studentIds).stream()
                .collect(Collectors.toMap(
                        mark -> mark.getStudent().getId(),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private static MarkSheet toMarkSheet(
            SheetContext context,
            List<Student> roster,
            Map<Long, Mark> marksByStudent
    ) {
        List<MarkRow> rows = roster.stream()
                .map(student -> {
                    Mark mark = marksByStudent.get(student.getId());
                    return new MarkRow(
                            student.getId(),
                            student.getRollNo(),
                            student.getAdmissionNo(),
                            student.getFullName(),
                            mark != null ? mark.getMarksObtained() : null,
                            mark != null && mark.isAbsent()
                    );
                })
                .toList();

        return new MarkSheet(
                context.exam().getId(),
                context.exam().getName(),
                context.schoolClass().getId(),
                context.schoolClass().getDisplayName(),
                context.subject().getId(),
                context.subject().getCode(),
                context.subject().getName(),
                context.subject().getFullMarks(),
                context.subject().getPassMarks(),
                context.exam().getStatus() == ExamStatus.DRAFT,
                rows
        );
    }

    private record SheetContext(Exam exam, SchoolClass schoolClass, Subject subject) {
    }

    private record ValidatedRow(Student student, BigDecimal marksObtained, boolean absent) {
    }
}
