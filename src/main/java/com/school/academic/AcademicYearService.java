package com.school.academic;

import com.school.academic.AcademicDtos.AcademicYearRequest;
import com.school.academic.AcademicDtos.AcademicYearResponse;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Academic years: newest first, unique names (ignoring case), end strictly after start,
 * exactly one current year once any year exists.
 */
@Service
public class AcademicYearService {

    static final String ENTITY_TYPE = "AcademicYear";

    private final AcademicYearRepository academicYearRepository;
    private final AuditService auditService;

    public AcademicYearService(AcademicYearRepository academicYearRepository, AuditService auditService) {
        this.academicYearRepository = academicYearRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponse> list() {
        return academicYearRepository.findAllByOrderByStartDateDescIdDesc().stream()
                .map(AcademicYearResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse get(Long id) {
        return AcademicYearResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse getCurrent() {
        return AcademicYearResponse.from(currentYear());
    }

    /** For other features (classes, exams, attendance) that need the current year entity. */
    @Transactional(readOnly = true)
    public AcademicYear currentYear() {
        return academicYearRepository.findByCurrentTrue()
                .orElseThrow(() -> new NotFoundException("No current academic year is set"));
    }

    @Transactional
    public AcademicYearResponse create(AcademicYearRequest request) {
        String name = request.name().trim();
        checkDates(request);
        if (academicYearRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Academic year " + name + " already exists");
        }
        AcademicYear year = new AcademicYear(name, request.startDate(), request.endDate());
        // The first year (no current one yet) becomes the current year automatically.
        year.setCurrent(!academicYearRepository.existsByCurrentTrue());
        AcademicYear saved = academicYearRepository.save(year);
        auditService.record("ACADEMIC_YEAR_CREATED", ENTITY_TYPE, String.valueOf(saved.getId()),
                "Created academic year " + saved.getName() + (saved.isCurrent() ? " (current)" : ""));
        return AcademicYearResponse.from(saved);
    }

    @Transactional
    public AcademicYearResponse update(Long id, AcademicYearRequest request) {
        AcademicYear year = findOrThrow(id);
        String name = request.name().trim();
        checkDates(request);
        if (academicYearRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Academic year " + name + " already exists");
        }
        year.setName(name);
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        return AcademicYearResponse.from(academicYearRepository.saveAndFlush(year));
    }

    /** Makes this year the current one. Clearing the others and setting this one share one transaction. */
    @Transactional
    public AcademicYearResponse activate(Long id) {
        AcademicYear year = findOrThrow(id);
        if (year.isCurrent()) {
            return AcademicYearResponse.from(year);
        }
        // Step 1: one UPDATE sets current = false on the old year (the persistence context is cleared).
        academicYearRepository.clearCurrent();
        // Step 2: reload this year fresh and mark it current.
        AcademicYear fresh = findOrThrow(id);
        fresh.setCurrent(true);
        AcademicYear saved = academicYearRepository.saveAndFlush(fresh);
        auditService.record("ACADEMIC_YEAR_ACTIVATED", ENTITY_TYPE, String.valueOf(saved.getId()),
                "Made " + saved.getName() + " the current academic year");
        return AcademicYearResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        AcademicYear year = findOrThrow(id);
        if (year.isCurrent()) {
            throw new ConflictException("The current academic year cannot be deleted");
        }
        if (academicYearRepository.hasClassesOrExams(id)) {
            throw new ConflictException("This academic year has classes or exams and cannot be deleted");
        }
        academicYearRepository.delete(year);
    }

    private AcademicYear findOrThrow(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Academic year", id));
    }

    private static void checkDates(AcademicYearRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessRuleException("End date must be after start date");
        }
    }
}
