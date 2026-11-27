package com.school.fee;

import com.school.academic.AcademicYear;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
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
import com.school.student.StudentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeInvoiceServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-26T06:00:00Z"),
            ZoneId.of("Asia/Dhaka")
    );
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private static final Pattern INVOICE_NO_PATTERN =
            Pattern.compile("^INV-202611-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$");

    @Mock
    private FeeInvoiceRepository feeInvoiceRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private AuditService auditService;

    private FeeInvoiceService service;

    @BeforeEach
    void setUp() {
        service = new FeeInvoiceService(
                feeInvoiceRepository,
                studentRepository,
                schoolClassRepository,
                studentAccessGuard,
                auditService,
                FIXED_CLOCK
        );
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username,
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
    }

    private Student student(Long id, String admissionNo, StudentStatus status) {
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        SchoolClass schoolClass = new SchoolClass(year, "Class 9", "A", "101", 40, null);
        ReflectionTestUtils.setField(schoolClass, "id", 10L);
        Student s = new Student(admissionNo);
        ReflectionTestUtils.setField(s, "id", id);
        s.setFirstName("Amina");
        s.setLastName("Islam");
        s.setStatus(status);
        s.setSchoolClass(schoolClass);
        return s;
    }

    @Test
    void create_forActiveStudent_generatesInvoiceNoAndAudits() {
        Student amina = student(1L, "ADM-2026-001", StudentStatus.ACTIVE);
        when(studentRepository.findByIdWithClassAndUser(1L)).thenReturn(Optional.of(amina));
        when(feeInvoiceRepository.existsByInvoiceNo(any())).thenReturn(false);
        when(feeInvoiceRepository.saveAndFlush(any(FeeInvoice.class))).thenAnswer(inv -> {
            FeeInvoice entity = inv.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 77L);
            return entity;
        });

        InvoiceResponse response = service.create(new InvoiceRequest(
                1L, "  Tuition Fee - November 2026  ", new BigDecimal("2000.00"), null, TODAY.minusDays(1)));

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.invoiceNo()).matches(INVOICE_NO_PATTERN);
        assertThat(response.title()).isEqualTo("Tuition Fee - November 2026");
        assertThat(response.discount()).isEqualByComparingTo("0.00");
        assertThat(response.payable()).isEqualByComparingTo("2000.00");
        assertThat(response.outstanding()).isEqualByComparingTo("2000.00");
        assertThat(response.status()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(response.overdue()).isTrue();
        verify(auditService).record(
                eq(FeeInvoiceService.AUDIT_INVOICE_CREATED),
                eq(FeeInvoiceService.ENTITY_TYPE),
                eq("77"),
                any()
        );
    }

    @Test
    void create_forInactiveStudent_throws422() {
        Student inactive = student(2L, "ADM-2026-002", StudentStatus.GRADUATED);
        when(studentRepository.findByIdWithClassAndUser(2L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(new InvoiceRequest(
                2L, "Tuition", new BigDecimal("1000.00"), BigDecimal.ZERO, TODAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invoices can only be created for active students");
    }

    @Test
    void create_forMissingStudent_throws404() {
        when(studentRepository.findByIdWithClassAndUser(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new InvoiceRequest(
                99L, "Tuition", new BigDecimal("1000.00"), BigDecimal.ZERO, TODAY)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Student 99 was not found");
    }

    @Test
    void bulkCreate_skipsAlreadyInvoicedStudentsIgnoringCase() {
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        SchoolClass schoolClass = new SchoolClass(year, "Class 9", "A", "101", 40, null);
        ReflectionTestUtils.setField(schoolClass, "id", 10L);
        Student s1 = student(1L, "ADM-1", StudentStatus.ACTIVE);
        Student s2 = student(2L, "ADM-2", StudentStatus.ACTIVE);

        when(schoolClassRepository.findById(10L)).thenReturn(Optional.of(schoolClass));
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of(s1, s2));
        when(feeInvoiceRepository.findStudentIdsWithActiveInvoiceTitle(List.of(1L, 2L), "Tuition Fee"))
                .thenReturn(Set.of(1L));
        when(feeInvoiceRepository.existsByInvoiceNo(any())).thenReturn(false);

        BulkResult result = service.bulkCreate(new BulkInvoiceRequest(
                10L, " Tuition Fee ", new BigDecimal("1500.00"), TODAY.plusDays(10)));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeeInvoice>> captor = ArgumentCaptor.forClass(List.class);
        verify(feeInvoiceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getStudent().getId()).isEqualTo(2L);
        assertThat(captor.getValue().get(0).getInvoiceNo()).matches(INVOICE_NO_PATTERN);
        verify(auditService).record(
                eq(FeeInvoiceService.AUDIT_INVOICES_BULK_CREATED),
                eq("SchoolClass"),
                eq("10"),
                any()
        );
    }

    @Test
    void bulkCreate_whenClassHasNoActiveStudents_throws422() {
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        SchoolClass schoolClass = new SchoolClass(year, "Class 9", "A", "101", 40, null);
        when(schoolClassRepository.findById(10L)).thenReturn(Optional.of(schoolClass));
        when(studentRepository.findActiveRoster(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.bulkCreate(new BulkInvoiceRequest(
                10L, "Tuition", new BigDecimal("1500.00"), TODAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The class has no active students");
    }

    @Test
    void cancel_unpaidInvoice_cancelsAndAudits() {
        Student s = student(1L, "ADM-1", StudentStatus.ACTIVE);
        FeeInvoice invoice = new FeeInvoice("INV-202611-234567", s, "Lab Fee",
                new BigDecimal("500.00"), BigDecimal.ZERO, TODAY);
        ReflectionTestUtils.setField(invoice, "id", 55L);
        when(feeInvoiceRepository.findByIdWithStudentAndClass(55L)).thenReturn(Optional.of(invoice));
        when(feeInvoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);

        InvoiceResponse response = service.cancel(55L);

        assertThat(response.status()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(response.overdue()).isFalse();
        verify(auditService).record(
                eq(FeeInvoiceService.AUDIT_INVOICE_CANCELLED),
                eq(FeeInvoiceService.ENTITY_TYPE),
                eq("55"),
                any()
        );
    }

    @Test
    void cancel_invoiceWithPayment_throws409() {
        Student s = student(1L, "ADM-1", StudentStatus.ACTIVE);
        FeeInvoice invoice = new FeeInvoice("INV-202611-234567", s, "Lab Fee",
                new BigDecimal("500.00"), BigDecimal.ZERO, TODAY);
        invoice.applyPayment(new BigDecimal("200.00"));
        when(feeInvoiceRepository.findByIdWithStudentAndClass(55L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.cancel(55L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invoice INV-202611-234567 already has payments and cannot be cancelled");
    }

    @Test
    void summary_calculatesTotalsAndOneDecimalRate_andHandlesEmpty() {
        when(feeInvoiceRepository.sumActiveTotals()).thenReturn(new FeeTotalsProjection() {
            @Override
            public BigDecimal getTotalBilled() {
                return new BigDecimal("3000.00");
            }

            @Override
            public BigDecimal getTotalCollected() {
                return new BigDecimal("1000.00");
            }
        });
        when(feeInvoiceRepository.countOverdue(TODAY)).thenReturn(2L);

        FeeSummary summary = service.summary();
        assertThat(summary.totalBilled()).isEqualByComparingTo("3000.00");
        assertThat(summary.totalCollected()).isEqualByComparingTo("1000.00");
        assertThat(summary.totalOutstanding()).isEqualByComparingTo("2000.00");
        assertThat(summary.overdueInvoices()).isEqualTo(2L);
        assertThat(summary.collectionRate()).isEqualByComparingTo("33.3");

        when(feeInvoiceRepository.sumActiveTotals()).thenReturn(null);
        when(feeInvoiceRepository.countOverdue(TODAY)).thenReturn(0L);
        FeeSummary empty = service.summary();
        assertThat(empty.totalBilled()).isEqualByComparingTo("0.00");
        assertThat(empty.totalCollected()).isEqualByComparingTo("0.00");
        assertThat(empty.totalOutstanding()).isEqualByComparingTo("0.00");
        assertThat(empty.collectionRate()).isEqualByComparingTo("0.0");
    }

    @Test
    void search_rejectsUnknownSortField() {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by("unknownField"));
        assertThatThrownBy(() -> service.search(null, null, null, false, pageable))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void search_passesSpecAndDefaultSortWhenUnsorted() {
        when(feeInvoiceRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        service.search("tuition", InvoiceStatus.UNPAID, 10L, true, PageRequest.of(0, 20));

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(feeInvoiceRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("dueDate")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("dueDate").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getAndListForStudent_denyTeacher_andRestrictStudentToSelf() {
        authenticate("teacher1", "TEACHER");
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.listForStudent(1L)).isInstanceOf(ForbiddenException.class);

        Student self = student(1L, "ADM-1", StudentStatus.ACTIVE);
        authenticate("adm-1", "STUDENT");
        when(studentAccessGuard.currentStudent()).thenReturn(self);
        assertThatThrownBy(() -> service.listForStudent(2L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(StudentAccessGuard.OWN_RECORDS_ONLY);

        Student other = student(2L, "ADM-2", StudentStatus.ACTIVE);
        FeeInvoice otherInv = new FeeInvoice("INV-202611-234567", other, "Fee",
                new BigDecimal("100.00"), BigDecimal.ZERO, TODAY);
        when(feeInvoiceRepository.findByIdWithStudentAndClass(9L)).thenReturn(Optional.of(otherInv));
        assertThatThrownBy(() -> service.get(9L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(StudentAccessGuard.OWN_RECORDS_ONLY);
    }
}
