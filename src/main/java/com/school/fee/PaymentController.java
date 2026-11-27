package com.school.fee;

import com.school.fee.FeeDtos.PaymentRequest;
import com.school.fee.FeeDtos.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fees")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Any login; service restricts to ADMIN or the STUDENT who owns the invoice (TEACHER always 403). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/invoices/{id}/payments")
    public ResponseEntity<List<PaymentResponse>> listForInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.listForInvoice(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/invoices/{id}/payments")
    public ResponseEntity<PaymentResponse> recordPayment(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentResponse recorded = paymentService.recordPayment(id, request, idempotencyKey);
        return ResponseEntity
                .created(URI.create("/api/v1/fees/invoices/" + id + "/payments"))
                .body(recorded);
    }
}
