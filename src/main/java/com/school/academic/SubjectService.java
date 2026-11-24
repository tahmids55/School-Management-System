package com.school.academic;

import com.school.academic.AcademicDtos.SubjectRequest;
import com.school.academic.AcademicDtos.SubjectResponse;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Subjects: ordered by code, code trimmed and upper-case and unique, pass marks never above full marks.
 */
@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> list() {
        return subjectRepository.findAllByOrderByCodeAsc().stream()
                .map(SubjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(Long id) {
        return SubjectResponse.from(findOrThrow(id));
    }

    @Transactional
    public SubjectResponse create(SubjectRequest request) {
        String code = normalizeCode(request.code());
        checkMarks(request);
        if (subjectRepository.existsByCode(code)) {
            throw new ConflictException("Subject code " + code + " already exists");
        }
        Subject subject = new Subject(code, request.name().trim(), request.fullMarks(), request.passMarks());
        return SubjectResponse.from(subjectRepository.save(subject));
    }

    @Transactional
    public SubjectResponse update(Long id, SubjectRequest request) {
        Subject subject = findOrThrow(id);
        String code = normalizeCode(request.code());
        checkMarks(request);
        if (subjectRepository.existsByCodeAndIdNot(code, id)) {
            throw new ConflictException("Subject code " + code + " already exists");
        }
        subject.setCode(code);
        subject.setName(request.name().trim());
        subject.setFullMarks(request.fullMarks());
        subject.setPassMarks(request.passMarks());
        return SubjectResponse.from(subjectRepository.saveAndFlush(subject));
    }

    /**
     * Flushing here makes a foreign-key violation (subject used by classes or marks) surface
     * inside this call, so GlobalExceptionHandler answers 409 instead of a late commit error.
     */
    @Transactional
    public void delete(Long id) {
        Subject subject = findOrThrow(id);
        subjectRepository.delete(subject);
        subjectRepository.flush();
    }

    private Subject findOrThrow(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Subject", id));
    }

    static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static void checkMarks(SubjectRequest request) {
        if (request.passMarks() > request.fullMarks()) {
            throw new BusinessRuleException("Pass marks cannot be greater than full marks");
        }
    }
}
