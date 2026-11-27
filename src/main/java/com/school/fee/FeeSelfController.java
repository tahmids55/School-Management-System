package com.school.fee;

import com.school.fee.FeeDtos.OnlinePaymentRequest;
import com.school.fee.FeeDtos.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasRole('STUDENT')")
public class FeeSelfController {

    private final PaymentService paymentService;

    public FeeSelfController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/invoices/{invoiceId}/pay")
    public ResponseEntity<PaymentResponse> payInvoice(
            @PathVariable Long invoiceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OnlinePaymentRequest request
    ) {
        PaymentResponse recorded = paymentService.recordSelfPayment(invoiceId, request, idempotencyKey);
        return ResponseEntity
                .created(URI.create("/api/v1/fees/invoices/" + invoiceId + "/payments"))
                .body(recorded);
    }
}
