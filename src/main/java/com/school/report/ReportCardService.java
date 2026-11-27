package com.school.report;

import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.common.SecurityUtils;
import com.school.common.exception.ForbiddenException;
import com.school.common.exception.NotFoundException;
import com.school.exam.Exam;
import com.school.exam.ExamDtos.ClassResult;
import com.school.exam.ExamDtos.StudentResultRow;
import com.school.exam.ExamDtos.SubjectResult;
import com.school.exam.ExamRepository;
import com.school.exam.ExamStatus;
import com.school.exam.ResultService;
import com.school.security.StudentAccessGuard;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class ReportCardService {

    public static final String EXAM_NOT_PUBLISHED_MESSAGE = "Results of this exam are not published yet";

    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ResultService resultService;
    private final StudentAccessGuard studentAccessGuard;
    private final ReportRendererFactory rendererFactory;
    private final Clock clock;
    private final String schoolName;

    public ReportCardService(
            ExamRepository examRepository,
            StudentRepository studentRepository,
            SchoolClassRepository schoolClassRepository,
            ResultService resultService,
            StudentAccessGuard studentAccessGuard,
            ReportRendererFactory rendererFactory,
            Clock clock,
            @Value("${app.school.name:Greenfield High School}") String schoolName
    ) {
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.resultService = resultService;
        this.studentAccessGuard = studentAccessGuard;
        this.rendererFactory = rendererFactory;
        this.clock = clock;
        this.schoolName = schoolName;
    }

    public record RenderedReport(byte[] content, ReportFormat format, String fileName) {
    }

    @Transactional(readOnly = true)
    public RenderedReport generate(Long examId, Long studentId, ReportFormat format) {
        ReportFormat resolvedFormat = format == null ? ReportFormat.PDF : format;

        Exam exam = examRepository.findByIdWithAcademicYear(examId)
                .orElseThrow(() -> new NotFoundException("Exam", examId));
        Student student = studentRepository.findByIdWithClassAndUser(studentId)
                .orElseThrow(() -> new NotFoundException("Student", studentId));

        // ADMIN: any; TEACHER: only checkCanViewStudent; STUDENT: own only
        studentAccessGuard.checkCanViewStudent(studentId);

        if (SecurityUtils.hasRole("STUDENT") && exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new ForbiddenException(EXAM_NOT_PUBLISHED_MESSAGE);
        }

        if (student.getSchoolClass() == null) {
            throw new NotFoundException("Student " + studentId + " is not assigned to a class");
        }
        SchoolClass schoolClass = schoolClassRepository.findByIdWithYear(student.getSchoolClass().getId())
                .orElseThrow(() -> new NotFoundException("Student " + studentId + " is not assigned to a class"));

        if (!schoolClass.getAcademicYear().getId().equals(exam.getAcademicYear().getId())) {
            throw new NotFoundException("No result for this student in " + exam.getName());
        }

        ClassResult classResult = resultService.computeClassResult(exam, schoolClass);
        StudentResultRow row = classResult.students().stream()
                .filter(item -> item.studentId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No result for this student in " + exam.getName()));

        List<ReportCard.Line> lines = row.subjects().stream()
                .map(ReportCardService::toLine)
                .toList();

        ReportCard card = ReportCard.builder()
                .schoolName(schoolName)
                .examName(exam.getName())
                .academicYearName(exam.getAcademicYear().getName())
                .gradingScheme(classResult.gradingScheme())
                .studentName(row.studentName())
                .admissionNo(row.admissionNo())
                .className(classResult.className())
                .rollNo(row.rollNo())
                .lines(lines)
                .totalObtained(row.totalObtained())
                .totalFullMarks(row.totalFullMarks())
                .percentage(row.percentage())
                .gpa(row.gpa())
                .letter(row.letter())
                .passed(row.passed())
                .position(row.position())
                .classSize(classResult.students().size())
                .generatedAt(clock.instant())
                .build();

        byte[] bytes = rendererFactory.create(resolvedFormat).render(card);
        String fileName = "report-card-" + studentId + "-" + examId + "." + resolvedFormat.extension();
        return new RenderedReport(bytes, resolvedFormat, fileName);
    }

    private static ReportCard.Line toLine(SubjectResult subject) {
        String obtained = subject.absent() || subject.obtained() == null
                ? "AB"
                : subject.obtained().stripTrailingZeros().toPlainString();
        return new ReportCard.Line(
                subject.subjectCode(),
                subject.subjectName(),
                subject.fullMarks(),
                obtained,
                subject.letter(),
                subject.point(),
                subject.passed()
        );
    }
}
