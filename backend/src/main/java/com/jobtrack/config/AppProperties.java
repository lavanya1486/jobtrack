package com.jobtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Strongly typed {@code app.*} configuration.
 *
 * <p>Everything environment specific lives here and is bound from environment variables in
 * production (see {@code application.yml} and the README). No secret has a usable default: the JWT
 * secret must be provided, otherwise the app refuses to start.</p>
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue Cors cors,
        @DefaultValue Reminders reminders,
        @DefaultValue Seed seed
) {

    /**
     * @param secret           HMAC signing key, at least 32 characters (256 bits)
     * @param accessTokenTtl   how long an access token is valid
     * @param refreshTokenTtl  how long a refresh token is valid
     * @param issuer           {@code iss} claim, must match on validation
     */
    public record Jwt(
            String secret,
            @DefaultValue("30m") Duration accessTokenTtl,
            @DefaultValue("7d") Duration refreshTokenTtl,
            @DefaultValue("jobtrack-api") String issuer
    ) {
    }

    /**
     * @param allowedOrigins exact browser origins allowed to call the API (no wildcards with
     *                       credentials); configured per environment
     */
    public record Cors(
            @DefaultValue({"http://localhost:5173", "http://127.0.0.1:5173"}) List<String> allowedOrigins
    ) {
    }

    /**
     * @param enabled              run the reminder scheduler at all
     * @param cron                 Spring cron expression for the generation job
     * @param deadlineLeadTime     how far before a posting deadline the reminder appears
     * @param interviewPrepLeadTime how far before an interview the prep reminder appears
     * @param followUpAfter        days without a response after which a follow-up reminder appears
     * @param lookAheadDays        window scanned on each run
     * @param emailEnabled         send e-mail reminders (requires SMTP configuration)
     * @param emailLeadTime        how early before due time the e-mail goes out
     */
    public record Reminders(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 */15 * * * *") String cron,
            @DefaultValue("48h") Duration deadlineLeadTime,
            @DefaultValue("24h") Duration interviewPrepLeadTime,
            @DefaultValue("7d") Duration followUpAfter,
            @DefaultValue("14") int lookAheadDays,
            @DefaultValue("false") boolean emailEnabled,
            @DefaultValue("24h") Duration emailLeadTime,
            @DefaultValue("0 0 8 * * *") String emailCron,
            @DefaultValue("0 30 3 * * *") String housekeepingCron,
            @DefaultValue("no-reply@jobtrack.local") String emailFrom
    ) {
    }

    /**
     * Optional demo data seeding, off by default so a fresh production database stays empty.
     */
    public record Seed(
            @DefaultValue("false") boolean demoData,
            @DefaultValue("demo@jobtrack.local") String demoEmail,
            @DefaultValue("Demo1234!") String demoPassword,
            @DefaultValue("admin@jobtrack.local") String adminEmail,
            @DefaultValue("Admin1234!") String adminPassword
    ) {
    }
}
