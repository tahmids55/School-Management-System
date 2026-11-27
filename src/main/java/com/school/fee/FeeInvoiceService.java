package com.school.fee;

import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.audit.AuditService;
import com.school.common.PageResponse;
import com.school.common.SecurityUtils;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ForbiddenException;
import com.school.common.exception.NotFoundException;
import com.school.fee.FeeDtos.BulkInvoiceRequest;
import com.school.fee.FeeDtos.BulkResult;
import com.school.fee.FeeDtos.FeeSummary;
import com.school.fee.FeeDtos.InvoiceRequest;
import com.school.fee.FeeDtos.InvoiceResponse;
import com.school.fee.FeeInvoiceRepository.FeeTotalsProjection;
import com.school.security.StudentAccessGuard;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fee invoice billing, search, cancellation, and summary.
 * Teachers have zero fee access; students can only view their own invoices.
 */
@Service
public class FeeInvoiceService {

    static final String ENTITY_TYPE = "FeeInvoice";
    public static final String AUDIT_INVOICE_CREATED = "INVOICE_CREATED";
    public static final String AUDIT_INVOICES_BULK_CREATED = "INVOICES_BULK_CREATED";
    public static final String AUDIT_INVOICE_CANCELLED = "INVOICE_CANCELLED";

    private static final String INVOICE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_LENGTH = 6;
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("dueDate"),
            Sort.Order.desc("id")
    );
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "invoiceNo", "title", "amount", "discount", "amountPaid", "dueDate", "status", "createdAt", "updatedAt"
    );

    private final FeeInvoiceRepository feeInvoiceRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final AuditService auditService;
    private final Clock clock;

    public FeeInvoiceService(
            FeeInvoiceRepository feeInvoiceRepository,
            StudentRepository studentRepository,
            SchoolClassRepository schoolClassRepository,
            StudentAccessGuard studentAccessGuard,
            AuditService auditService,
            Clock clock
    ) {
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(
            String q,
            InvoiceStatus status,
            Long classId,
            boolean overdue,
            Pageable pageable
    ) {
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        validateSort(effectivePageable.getSort());

        LocalDate today = LocalDate.now(clock);
        Specification<FeeInvoice> spec = FeeInvoiceSpecifications.search(q, status, classId, overdue, today);
        Page<FeeInvoice> page = feeInvoiceRepository.findAll(spec, effectivePageable);
        return PageResponse.from(page, invoice -> InvoiceResponse.from(invoice, today));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        if (SecurityUtils.hasRole("ADMIN")) {
            FeeInvoice invoice = feeInvoiceRepository.findByIdWithStudentAndClass(id)
                    .orElseThrow(() -> new NotFoundException("Invoice", id));
            return InvoiceResponse.from(invoice, LocalDate.now(clock));
        }
        if (SecurityUtils.hasRole("STUDENT")) {
            Student current = studentAccessGuard.currentStudent();
            FeeInvoice invoice = feeInvoiceRepository.findByIdWithStudentAndClass(id)
                    .orElseThrow(() -> new ForbiddenException(StudentAccessGuard.OWN_RECORDS_ONLY));
            if (!invoice.getStudent().getId().equals(current.getId())) {
                throw new ForbiddenException(StudentAccessGuard.OWN_RECORDS_ONLY);
            }
            return InvoiceResponse.from(invoice, LocalDate.now(clock));
        }
        throw new ForbiddenException("You do not have permission to view fee records");
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listForStudent(Long studentId) {
        if (SecurityUtils.hasRole("ADMIN")) {
            if (!studentRepository.existsById(studentId)) {
                throw new NotFoundException("Student", studentId);
            }
        } else if (SecurityUtils.hasRole("STUDENT")) {
            Student current = studentAccessGuard.currentStudent();
            if (!current.getId().equals(studentId)) {
                throw new ForbiddenException(StudentAccessGuard.OWN_RECORDS_ONLY);
            }
        } else {
            throw new ForbiddenException("You do not have permission to view fee records");
        }

        LocalDate today = LocalDate.now(clock);
        return feeInvoiceRepository.findAllByStudentIdWithDetails(studentId).stream()
                .map(invoice -> InvoiceResponse.from(invoice, today))
                .toList();
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        Student student = studentRepository.findByIdWithClassAndUser(request.studentId())
                .orElseThrow(() -> new NotFoundException("Student", request.studentId()));
        if (!student.isActive()) {
            throw new BusinessRuleException("Invoices can only be created for active students");
        }

        LocalDate today = LocalDate.now(clock);
        String invoiceNo = generateUniqueInvoiceNo(today, Set.of());
        FeeInvoice invoice = new FeeInvoice(
                invoiceNo,
                student,
                request.title().trim(),
                request.amount(),
                request.discount(),
                request.dueDate()
        );

        FeeInvoice saved = feeInvoiceRepository.saveAndFlush(invoice);
        auditService.record(
                AUDIT_INVOICE_CREATED,
                ENTITY_TYPE,
                String.valueOf(saved.getId()),
                "Created invoice " + saved.getInvoiceNo() + " for " + student.getAdmissionNo()
                        + " (" + saved.payable() + " BDT)"
        );
        return InvoiceResponse.from(saved, today);
    }

    @Transactional
    public BulkResult bulkCreate(BulkInvoiceRequest request) {
        SchoolClass schoolClass = schoolClassRepository.findById(request.classId())
                .orElseThrow(() -> new NotFoundException("Class", request.classId()));
        List<Student> activeStudents = studentRepository.findActiveRoster(request.classId());
        if (activeStudents.isEmpty()) {
            throw new BusinessRuleException("The class has no active students");
        }

        String title = request.title().trim();
        List<Long> studentIds = activeStudents.stream().map(Student::getId).toList();
        Set<Long> alreadyInvoiced = feeInvoiceRepository.findStudentIdsWithActiveInvoiceTitle(studentIds, title);

        LocalDate today = LocalDate.now(clock);
        Set<String> generatedNumbers = new HashSet<>();
        List<FeeInvoice> toCreate = new ArrayList<>();
        int skipped = 0;

        for (Student student : activeStudents) {
            if (alreadyInvoiced.contains(student.getId())) {
                skipped++;
                continue;
            }
            String invoiceNo = generateUniqueInvoiceNo(today, generatedNumbers);
            generatedNumbers.add(invoiceNo);
            toCreate.add(new FeeInvoice(
                    invoiceNo,
                    student,
                    title,
                    request.amount(),
                    BigDecimal.ZERO,
                    request.dueDate()
            ));
        }

        if (!toCreate.isEmpty()) {
            feeInvoiceRepository.saveAll(toCreate);
            feeInvoiceRepository.flush();
        }

        auditService.record(
                AUDIT_INVOICES_BULK_CREATED,
                "SchoolClass",
                String.valueOf(schoolClass.getId()),
                "Bulk created " + toCreate.size() + " invoices (" + skipped + " skipped) for "
                        + schoolClass.getDisplayName() + ": " + title
        );
        return new BulkResult(toCreate.size(), skipped);
    }

    @Transactional
    public InvoiceResponse cancel(Long id) {
        FeeInvoice invoice = feeInvoiceRepository.findByIdWithStudentAndClass(id)
                .orElseThrow(() -> new NotFoundException("Invoice", id));
        invoice.cancel();
        FeeInvoice saved = feeInvoiceRepository.saveAndFlush(invoice);

        auditService.record(
                AUDIT_INVOICE_CANCELLED,
                ENTITY_TYPE,
                String.valueOf(saved.getId()),
                "Cancelled invoice " + saved.getInvoiceNo()
        );
        return InvoiceResponse.from(saved, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public FeeSummary summary() {
        LocalDate today = LocalDate.now(clock);
        FeeTotalsProjection totals = feeInvoiceRepository.sumActiveTotals();
        long overdueCount = feeInvoiceRepository.countOverdue(today);
        return FeeSummary.of(
                totals != null ? totals.getTotalBilled() : null,
                totals != null ? totals.getTotalCollected() : null,
                overdueCount
        );
    }

    private String generateUniqueInvoiceNo(LocalDate today, Set<String> reserved) {
        String prefix = "INV-" + YEAR_MONTH.format(today) + "-";
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < RANDOM_LENGTH; i++) {
                sb.append(INVOICE_ALPHABET.charAt(RANDOM.nextInt(INVOICE_ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (!reserved.contains(candidate) && !feeInvoiceRepository.existsByInvoiceNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique invoice number");
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Invalid sort field: " + order.getProperty());
            }
        }
    }
}
