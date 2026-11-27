package com.school.fee;

import com.school.common.exception.BusinessRuleException;
import com.school.fee.gateway.BankTransferPaymentGateway;
import com.school.fee.gateway.BkashPaymentAdapter;
import com.school.fee.gateway.CashPaymentGateway;
import com.school.fee.gateway.FakeBkashSdk;
import com.school.fee.gateway.PaymentGateway;
import com.school.fee.gateway.PaymentGateway.ChargeRequest;
import com.school.fee.gateway.PaymentGateway.GatewayResult;
import com.school.fee.gateway.PaymentGatewayFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentGatewayAdapterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-27T06:00:00Z"),
            ZoneId.of("Asia/Dhaka")
    );

    @Test
    void bkashAdapter_translatesTwoStepSdkIntoOneCall() {
        FakeBkashSdk realSdk = new FakeBkashSdk();
        BkashPaymentAdapter realAdapter = new BkashPaymentAdapter(realSdk);

        String createdId = realSdk.createPayment("1500.00", "INV-202611-TEST01");
        assertThat(createdId).matches("^TR00[A-Z0-9]{12}$");
        assertThat(realSdk.executePayment("TR00INVALID").statusCode()).isEqualTo("2056");
        assertThat(realSdk.executePayment("TR00INVALID").statusMessage()).isEqualTo("Invalid payment ID");

        GatewayResult realResult = realAdapter.charge(
                new ChargeRequest("INV-202611-ABC123", new BigDecimal("2500.00"), "idem-1")
        );
        assertThat(realResult.success()).isTrue();
        assertThat(realResult.transactionRef()).startsWith("BK");
        assertThat(realResult.message()).isNull();

        FakeBkashSdk mockSdk = Mockito.mock(FakeBkashSdk.class);
        when(mockSdk.createPayment("1000.00", "INV-202611-XYZ999")).thenReturn("TR00ABCDEF123456");
        when(mockSdk.executePayment("TR00ABCDEF123456"))
                .thenReturn(new FakeBkashSdk.ExecuteResponse("0000", "BK999888777", "Successful"));

        BkashPaymentAdapter mockAdapter = new BkashPaymentAdapter(mockSdk);
        GatewayResult mockResult = mockAdapter.charge(
                new ChargeRequest("INV-202611-XYZ999", new BigDecimal("1000.00"), "idem-2")
        );

        assertThat(mockResult.success()).isTrue();
        assertThat(mockResult.transactionRef()).isEqualTo("BK999888777");
        verify(mockSdk).createPayment("1000.00", "INV-202611-XYZ999");
        verify(mockSdk).executePayment("TR00ABCDEF123456");
    }

    @Test
    void bkashAdapter_mapsSdkErrorCodeToDecline() {
        BkashPaymentAdapter adapter = new BkashPaymentAdapter(new FakeBkashSdk());

        GatewayResult limitExceeded = adapter.charge(
                new ChargeRequest("INV-202611-BIG001", new BigDecimal("50000.01"), "idem-limit")
        );
        assertThat(limitExceeded.success()).isFalse();
        assertThat(limitExceeded.transactionRef()).isNull();
        assertThat(limitExceeded.message()).isEqualTo("Transaction limit exceeded");

        FakeBkashSdk mockSdk = Mockito.mock(FakeBkashSdk.class);
        when(mockSdk.createPayment("500.00", "INV-202611-BADID")).thenReturn("TR00STALE000001");
        when(mockSdk.executePayment("TR00STALE000001"))
                .thenReturn(new FakeBkashSdk.ExecuteResponse("2056", null, "Invalid payment ID"));

        GatewayResult invalidId = new BkashPaymentAdapter(mockSdk)
                .charge(new ChargeRequest("INV-202611-BADID", new BigDecimal("500.00"), "idem-bad"));
        assertThat(invalidId.success()).isFalse();
        assertThat(invalidId.message()).isEqualTo("Invalid payment ID");
    }

    @Test
    void bankTransfer_requiresBankReference() {
        BankTransferPaymentGateway gateway = new BankTransferPaymentGateway();

        assertThatThrownBy(() -> gateway.charge(new ChargeRequest("INV-1", new BigDecimal("500.00"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Bank transaction reference is required");

        assertThatThrownBy(() -> gateway.charge(new ChargeRequest("INV-1", new BigDecimal("500.00"), "   ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Bank transaction reference is required");

        GatewayResult result = gateway.charge(new ChargeRequest("INV-1", new BigDecimal("500.00"), "  DBBL-9988  "));
        assertThat(result.success()).isTrue();
        assertThat(result.transactionRef()).isEqualTo("DBBL-9988");
    }

    @Test
    void cashGateway_usesClockDateAndSixDigits() {
        CashPaymentGateway gateway = new CashPaymentGateway(FIXED_CLOCK);

        GatewayResult result = gateway.charge(new ChargeRequest("INV-1", new BigDecimal("500.00"), "idem-cash"));
        assertThat(result.success()).isTrue();
        assertThat(result.transactionRef()).matches("^CASH-20261127-\\d{6}$");
    }

    @Test
    void factory_returnsTheGatewayForEachMethod() {
        CashPaymentGateway cash = new CashPaymentGateway(FIXED_CLOCK);
        BkashPaymentAdapter bkash = new BkashPaymentAdapter(new FakeBkashSdk());
        BankTransferPaymentGateway bank = new BankTransferPaymentGateway();

        PaymentGatewayFactory factory = new PaymentGatewayFactory(List.of(cash, bkash, bank));

        assertThat(factory.get(PaymentMethod.CASH)).isSameAs(cash);
        assertThat(factory.get(PaymentMethod.BKASH)).isSameAs(bkash);
        assertThat(factory.get(PaymentMethod.BANK_TRANSFER)).isSameAs(bank);

        PaymentGatewayFactory partial = new PaymentGatewayFactory(List.of(cash));
        assertThatThrownBy(() -> partial.get(PaymentMethod.BKASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment method BKASH is not supported");
    }
}
