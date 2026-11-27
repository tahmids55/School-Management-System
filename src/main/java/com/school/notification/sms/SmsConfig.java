package com.school.notification.sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Wires the fake provider behind the adapter. A real provider = a new adapter bean here, nothing else. */
@Configuration
public class SmsConfig {

    @Bean
    public FakeSmsGatewayClient fakeSmsGatewayClient() {
        return new FakeSmsGatewayClient();
    }

    @Bean
    public SmsSender smsSender(FakeSmsGatewayClient client, Environment environment) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        return new SmsGatewayAdapter(client, !production);
    }
}
