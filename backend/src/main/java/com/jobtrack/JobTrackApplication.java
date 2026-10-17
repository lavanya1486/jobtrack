package com.jobtrack;

import com.jobtrack.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the JobTrack REST API.
 *
 * <p>JobTrack is a job application tracker with analytics: candidates record every application,
 * schedule interview rounds, keep reminders and inspect funnel metrics computed from real
 * database data.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableConfigurationProperties(AppProperties.class)
public class JobTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobTrackApplication.class, args);
    }
}
