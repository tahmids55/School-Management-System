package com.school.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.school.time-zone:Asia/Dhaka}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
