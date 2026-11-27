package com.school.notification.sms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsGatewayAdapterTest {

    @Mock
    private FakeSmsGatewayClient client;

    @Test
    void send_convertsLocalNumberToMsisdnAndAcceptsStatus202() {
        when(client.submitMessage("8801711123456", "Hello", "SCHOOL")).thenReturn(202);
        SmsGatewayAdapter adapter = new SmsGatewayAdapter(client, false);

        boolean accepted = adapter.send("01711-123 456", "Hello");

        assertThat(accepted).isTrue();
        verify(client).submitMessage("8801711123456", "Hello", "SCHOOL");
    }

    @Test
    void send_providerErrorDoesNotThrow() {
        when(client.submitMessage(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("down"));
        SmsGatewayAdapter adapter = new SmsGatewayAdapter(client, false);

        assertThatCode(() -> assertThat(adapter.send("01711123456", "Hello")).isFalse()).doesNotThrowAnyException();
    }

    @Test
    void send_blankNumberIsSkipped() {
        SmsGatewayAdapter adapter = new SmsGatewayAdapter(client, false);

        assertThat(adapter.send("   ", "Hello")).isFalse();
        assertThat(adapter.send(null, "Hello")).isFalse();
        verifyNoInteractions(client);
    }

    @Test
    void send_otherStatusThanAcceptedIsFalse() {
        when(client.submitMessage("8801711123456", "Hello", "SCHOOL")).thenReturn(400);

        assertThat(new SmsGatewayAdapter(client, true).send("+8801711123456", "Hello")).isFalse();
    }

    @Test
    void toMsisdn_andMasking() {
        assertThat(SmsGatewayAdapter.toMsisdn("+880 1711-123456")).isEqualTo("8801711123456");
        assertThat(SmsGatewayAdapter.toMsisdn("01711123456")).isEqualTo("8801711123456");
        assertThat(SmsGatewayAdapter.masked("8801711123456")).isEqualTo("***456");
    }

    @Test
    void fakeClient_acceptsOnlyBangladeshiMobileNumbers() {
        FakeSmsGatewayClient real = new FakeSmsGatewayClient();
        assertThat(real.submitMessage("8801711123456", "Hi", "SCHOOL")).isEqualTo(202);
        assertThat(real.submitMessage("12345", "Hi", "SCHOOL")).isEqualTo(400);
    }
}
