package com.school.exam;

import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.NotFoundException;
import com.school.exam.ExamDtos.ClassResult;
import com.school.exam.ExamDtos.ExamResponse;
import com.school.exam.ExamDtos.MyExamResult;
import com.school.exam.ExamDtos.StudentResultRow;
import com.school.grading.GradingStrategy;
import com.school.grading.GradingStrategyResolver;
import com.school.security.AccessGuard;
import com.school.security.StudentAccessGuard;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResultService {

    public static final String AUDIT_RESULTS_PUBLISHED = "RESULTS_PUBLISHED";
    public static final String AUDIT_RESULTS_UNPUBLISHED = "RESULTS_UNPUBLISHED";
    public static final String NO_PUBLISHED_RESULT_MESSAGE = "No published result found for this exam";

    private static final int MAX_MISSING_PAIRS_LISTED = 5;

    private final ExamRepository examRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final StudentRepository studentRepository;
    private final MarkRepository markRepository;
    private final GradingStrategyResolver gradingStrategyResolver;
    private final ResultCalculator resultCalculator;
    private final AccessGuard accessGuard;
    private final StudentAccessGuard studentAccessGuard;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ResultService(
            ExamRepository examRepository,
            SchoolClassRepository schoolClassRepository,
            ClassSubjectRepository classSubjectRepository,
            StudentRepository studentRepository,
            MarkRepository markRepository,
            GradingStrategyResolver gradingStrategyResolver,
            ResultCalculator resultCalculator,
            AccessGuard accessGuard,
            StudentAccessGuard studentAccessGuard,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.examRepository = examRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.studentRepository = studentRepository;
        this.markRepository = markRepository;
        this.gradingStrategyResolver = gradingStrategyResolver;
        this.resultCalculator = resultCalculator;
        this.accessGuard = accessGuard;
        this.studentAccessGuard = studentAccessGuard;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ExamResponse publish(Long examId) {
        Exam exam = findExamOrThrow(examId);
        ensureMarksCompleteBeforePublish(exam);

        exam.publish(clock.instant());
        Exam saved = examRepository.saveAndFlush(exam);

        auditService.record(
                AUDIT_RESULTS_PUBLISHED,
                "Exam",
                String.valueOf(saved.getId()),
                "Published results of " + saved.getName()
        );
        eventPublisher.publishEvent(new ResultsPublishedEvent(saved.getId(), saved.getName()));
        return ExamResponse.from(saved);
    }

    @Transactional
    public ExamResponse unpublish(Long examId) {
        Exam exam = findExamOrThrow(examId);
        exam.unpublish();
        Exam saved = examRepository.saveAndFlush(exam);

        auditService.record(
                AUDIT_RESULTS_UNPUBLISHED,
                "Exam",
                String.valueOf(saved.getId()),
                "Unpublished results of " + saved.getName()
        );
        return ExamResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ClassResult getClassResult(Long examId, Long classId) {
        Exam exam = findExamOrThrow(examId);
        SchoolClass schoolClass = schoolClassRepository.findByIdWithYear(classId)
                .orElseThrow(() -> new NotFoundException("Class", classId));

        if (!schoolClass.getAcademicYear().getId().equals(exam.getAcademicYear().getId())) {
            throw new BusinessRuleException(
                    "Class " + schoolClass.getDisplayName()
                            + " does not belong to academic year " + exam.getAcademicYear().getName()
            );
        }

        accessGuard.checkCanManageClass(classId);
        return computeClassResult(exam, schoolClass);
    }

    @Transactional(readOnly = true)
    public List<MyExamResult> myResults() {
        Student student = studentAccessGuard.currentStudent();
        if (student.getSchoolClass() == null) {
            return List.of();
        }
        SchoolClass schoolClass = schoolClassRepository.findByIdWithYear(student.getSchoolClass().getId())
                .orElse(null);
        if (schoolClass == null) {
            return List.of();
        }

        List<Exam> publishedExams = examRepository.findByAcademicYearIdAndStatusOrderByEndDateDescIdDesc(
                schoolClass.getAcademicYear().getId(),
                ExamStatus.PUBLISHED
        );

        List<MyExamResult> results = new ArrayList<>(publishedExams.size());
        for (Exam exam : publishedExams) {
            ClassResult classResult = computeClassResult(exam, schoolClass);
            classResult.students().stream()
                    .filter(row -> row.studentId().equals(student.getId()))
                    .findFirst()
                    .ifPresent(row -> results.add(new MyExamResult(
                            exam.getId(),
                            exam.getName(),
                            exam.getEndDate(),
                            row.gpa(),
                            row.letter(),
                            row.passed(),
                            row.position()
                    )));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public StudentResultRow myExamResult(Long examId) {
        Student student = studentAccessGuard.currentStudent();
        if (student.getSchoolClass() == null) {
            throw new NotFoundException(NO_PUBLISHED_RESULT_MESSAGE);
        }
        SchoolClass schoolClass = schoolClassRepository.findByIdWithYear(student.getSchoolClass().getId())
                .orElseThrow(() -> new NotFoundException(NO_PUBLISHED_RESULT_MESSAGE));

        Exam exam = examRepository.findByIdWithAcademicYear(examId)
                .filter(e -> e.getStatus() == ExamStatus.PUBLISHED)
                .filter(e -> e.getAcademicYear().getId().equals(schoolClass.getAcademicYear().getId()))
                .orElseThrow(() -> new NotFoundException(NO_PUBLISHED_RESULT_MESSAGE));

        ClassResult classResult = computeClassResult(exam, schoolClass);
        return classResult.students().stream()
                .filter(row -> row.studentId().equals(student.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(NO_PUBLISHED_RESULT_MESSAGE));
    }

    private void ensureMarksCompleteBeforePublish(Exam exam) {
        List<SchoolClass> classes = schoolClassRepository.findAllByYearWithYear(exam.getAcademicYear().getId());
        boolean hasPublishablePair = false;
        List<String> missingPairs = new ArrayList<>();

        for (SchoolClass schoolClass : classes) {
            List<Subject> subjects = classSubjectRepository.findAllByClassIdWithDetails(schoolClass.getId()).stream()
                    .map(ClassSubject::getSubject)
                    .toList();
            if (subjects.isEmpty()) {
                continue;
            }
            List<Student> roster = studentRepository.findActiveRoster(schoolClass.getId());
            if (roster.isEmpty()) {
                continue;
            }

            hasPublishablePair = true;
            List<Long> studentIds = roster.stream().map(Student::getId).toList();
            List<Mark> marks = markRepository.findByExamIdAndStudentIdIn(exam.getId(), studentIds);

            Map<Long, Map<Long, Mark>> byStudent = new HashMap<>();
            for (Mark mark : marks) {
                byStudent.computeIfAbsent(mark.getStudent().getId(), ignored -> new HashMap<>())
                        .put(mark.getSubject().getId(), mark);
            }

            for (Subject subject : subjects) {
                boolean subjectComplete = true;
                for (Student student : roster) {
                    Mark mark = byStudent.getOrDefault(student.getId(), Map.of()).get(subject.getId());
                    if (mark == null || (!mark.isAbsent() && mark.getMarksObtained() == null)) {
                        subjectComplete = false;
                        break;
                    }
                }
                if (!subjectComplete) {
                    missingPairs.add(schoolClass.getDisplayName() + " / " + subject.getCode());
                }
            }
        }

        if (!hasPublishablePair) {
            throw new BusinessRuleException(
                    "Cannot publish results: there are no active students with assigned subjects in "
                            + exam.getAcademicYear().getName()
            );
        }

        if (!missingPairs.isEmpty()) {
            List<String> preview = missingPairs.size() > MAX_MISSING_PAIRS_LISTED
                    ? missingPairs.subList(0, MAX_MISSING_PAIRS_LISTED)
                    : missingPairs;
            String suffix = missingPairs.size() > MAX_MISSING_PAIRS_LISTED
                    ? " (and " + (missingPairs.size() - MAX_MISSING_PAIRS_LISTED) + " more)"
                    : "";
            throw new BusinessRuleException(
                    "Cannot publish results: missing marks for " + String.join(", ", preview) + suffix
            );
        }
    }

    /**
     * Computes a class result in 3 queries: subjects of the class, active roster, and all marks for that roster.
     * Exposed for {@code ReportCardService} after it has enforced its own role/ownership checks.
     */
    @Transactional(readOnly = true)
    public ClassResult computeClassResult(Exam exam, SchoolClass schoolClass) {
        List<Subject> subjects = classSubjectRepository.findAllByClassIdWithDetails(schoolClass.getId()).stream()
                .map(ClassSubject::getSubject)
                .toList();
        List<Student> roster = studentRepository.findActiveRoster(schoolClass.getId());
        List<Mark> marks = roster.isEmpty()
                ? List.of()
                : markRepository.findByExamIdAndStudentIdIn(
                        exam.getId(),
                        roster.stream().map(Student::getId).toList()
                );

        GradingStrategy strategy = gradingStrategyResolver.active();
        List<StudentResultRow> ranked = resultCalculator.calculateAndRank(roster, subjects, marks, strategy);

        int passedCount = (int) ranked.stream().filter(StudentResultRow::passed).count();
        int failedCount = ranked.size() - passedCount;

        return new ClassResult(
                exam.getId(),
                exam.getName(),
                exam.getStatus(),
                schoolClass.getId(),
                schoolClass.getDisplayName(),
                strategy.displayName(),
                passedCount,
                failedCount,
                ranked
        );
    }

    private Exam findExamOrThrow(Long examId) {
        return examRepository.findByIdWithAcademicYear(examId)
                .orElseThrow(() -> new NotFoundException("Exam", examId));
    }
}
