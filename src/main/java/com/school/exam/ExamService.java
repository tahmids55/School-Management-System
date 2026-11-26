package com.school.exam;

import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.exam.ExamDtos.ExamRequest;
import com.school.exam.ExamDtos.ExamResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ExamService {

    public static final String AUDIT_EXAM_CREATED = "EXAM_CREATED";
    public static final String AUDIT_EXAM_DELETED = "EXAM_DELETED";

    private final ExamRepository examRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AuditService auditService;

    public ExamService(
            ExamRepository examRepository,
            AcademicYearRepository academicYearRepository,
            AuditService auditService
    ) {
        this.examRepository = examRepository;
        this.academicYearRepository = academicYearRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> list(Long academicYearId) {
        Long yearId;
        if (academicYearId != null) {
            yearId = findAcademicYearOrThrow(academicYearId).getId();
        } else {
            AcademicYear current = academicYearRepository.findByCurrentTrue().orElse(null);
            if (current == null) {
                return List.of();
            }
            yearId = current.getId();
        }
        return examRepository.findByAcademicYearIdOrderByStartDateDescIdDesc(yearId).stream()
                .map(ExamResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamResponse get(Long id) {
        return ExamResponse.from(findExamOrThrow(id));
    }

    @Transactional
    public ExamResponse create(ExamRequest request) {
        AcademicYear year = findAcademicYearOrThrow(request.academicYearId());
        String trimmedName = request.name().trim();
        validateDates(request.startDate(), request.endDate(), year);

        if (examRepository.existsByAcademicYearIdAndNameIgnoreCase(year.getId(), trimmedName)) {
            throw new ConflictException(
                    "An exam named " + trimmedName + " already exists in " + year.getName()
            );
        }

        Exam exam = new Exam(year, trimmedName, request.startDate(), request.endDate());
        Exam saved = examRepository.saveAndFlush(exam);
        auditService.record(
                AUDIT_EXAM_CREATED,
                "Exam",
                String.valueOf(saved.getId()),
                "Created exam " + saved.getName() + " in " + year.getName()
        );
        return ExamResponse.from(saved);
    }

    @Transactional
    public ExamResponse update(Long id, ExamRequest request) {
        Exam exam = findExamOrThrow(id);
        exam.requireDraft();

        AcademicYear year = findAcademicYearOrThrow(request.academicYearId());
        String trimmedName = request.name().trim();
        validateDates(request.startDate(), request.endDate(), year);

        if (examRepository.existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(year.getId(), trimmedName, id)) {
            throw new ConflictException(
                    "An exam named " + trimmedName + " already exists in " + year.getName()
            );
        }

        exam.updateDetails(year, trimmedName, request.startDate(), request.endDate());
        Exam saved = examRepository.saveAndFlush(exam);
        return ExamResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Exam exam = findExamOrThrow(id);
        exam.requireDraft();
        String name = exam.getName();
        examRepository.delete(exam);
        auditService.record(
                AUDIT_EXAM_DELETED,
                "Exam",
                String.valueOf(id),
                "Deleted exam " + name
        );
    }

    private Exam findExamOrThrow(Long id) {
        return examRepository.findByIdWithAcademicYear(id)
                .orElseThrow(() -> new NotFoundException("Exam", id));
    }

    private AcademicYear findAcademicYearOrThrow(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Academic year", id));
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate, AcademicYear year) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleException("End date cannot be before start date");
        }
        if (!year.contains(startDate) || !year.contains(endDate)) {
            throw new BusinessRuleException("Exam dates must be inside academic year " + year.getName());
        }
    }
}
