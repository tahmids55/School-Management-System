package com.school.fee.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public FakeBkashSdk fakeBkashSdk() {
        return new FakeBkashSdk();
    }
}
