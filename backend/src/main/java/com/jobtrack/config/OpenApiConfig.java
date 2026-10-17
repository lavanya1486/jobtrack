package com.jobtrack.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * <p>Registers the {@code bearerAuth} scheme so the "Authorize" button in Swagger UI stores the JWT
 * and sends it with every request, and documents the ownership + status-code contract directly in
 * the API description.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI jobTrackOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JobTrack API")
                        .version("1.0.0")
                        .summary("Job application tracker with analytics, interviews and reminders")
                        .description("""
                                REST API behind the JobTrack dashboard.

                                ## Authentication
                                1. `POST /api/auth/register` to create an account (or use the seeded demo account).
                                2. `POST /api/auth/login` - returns an `accessToken` (JWT, 30 min) and a `refreshToken` (7 days).
                                3. Click **Authorize** and paste the access token, or send `Authorization: Bearer <accessToken>`.
                                4. When the access token expires, `POST /api/auth/refresh` with the refresh token.
                                   Refresh tokens are single-use: each refresh returns a new pair and revokes the old token.

                                ## Data ownership
                                Every application, interview and reminder belongs to the authenticated user.
                                Requesting somebody else's resource returns **404 Not Found** (not 403) so that ids cannot be
                                probed for existence. Endpoints under `/api/admin` require the `ADMIN` role and answer **403**
                                for ordinary users.

                                ## Analytics definitions
                                * `submittedApplications` = applications whose status is not `SAVED`; this is the denominator of every rate.
                                * `responseRate` = applications with a recorded employer response / submitted applications.
                                * `interviewRate` = applications that reached an interview stage (status or interview round) / submitted applications.
                                * `offerRate` = offers / submitted applications.
                                """)
                        .contact(new Contact().name("JobTrack").url("https://github.com/your-username/jobtrack"))
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url("/").description("This environment")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken returned by /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
