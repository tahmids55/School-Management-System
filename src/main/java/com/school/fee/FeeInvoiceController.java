package com.school.fee;

import com.school.common.PageResponse;
import com.school.fee.FeeDtos.BulkInvoiceRequest;
import com.school.fee.FeeDtos.BulkResult;
import com.school.fee.FeeDtos.FeeSummary;
import com.school.fee.FeeDtos.InvoiceRequest;
import com.school.fee.FeeDtos.InvoiceResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fees")
public class FeeInvoiceController {

    private final FeeInvoiceService feeInvoiceService;

    public FeeInvoiceController(FeeInvoiceService feeInvoiceService) {
        this.feeInvoiceService = feeInvoiceService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/invoices")
    public ResponseEntity<PageResponse<InvoiceResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "false") boolean overdue,
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(feeInvoiceService.search(q, status, classId, overdue, pageable));
    }

    /** Any login; service restricts to ADMIN or the STUDENT who owns the invoice (TEACHER always 403). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/invoices/{id}")
    public ResponseEntity<InvoiceResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(feeInvoiceService.get(id));
    }

    /** Any login; service restricts to ADMIN or the STUDENT themselves (TEACHER always 403). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/students/{studentId}/invoices")
    public ResponseEntity<List<InvoiceResponse>> listForStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(feeInvoiceService.listForStudent(studentId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/invoices")
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceRequest request) {
        InvoiceResponse created = feeInvoiceService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/fees/invoices/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/invoices/bulk")
    public ResponseEntity<BulkResult> bulkCreate(@Valid @RequestBody BulkInvoiceRequest request) {
        return ResponseEntity.ok(feeInvoiceService.bulkCreate(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/invoices/{id}/cancel")
    public ResponseEntity<InvoiceResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(feeInvoiceService.cancel(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<FeeSummary> summary() {
        return ResponseEntity.ok(feeInvoiceService.summary());
    }
}
