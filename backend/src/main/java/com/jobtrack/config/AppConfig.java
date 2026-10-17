package com.jobtrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Application-wide beans that are not security or API related.
 *
 * <p>{@link Clock} is injected instead of calling {@code Instant.now()} / {@code LocalDate.now()}
 * scattered through the code. Tests replace it with a fixed clock, which is what makes "deadline is
 * overdue" and "reminder is due" assertions deterministic.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
