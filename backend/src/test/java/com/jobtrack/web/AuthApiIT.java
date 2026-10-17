package com.jobtrack.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtrack.domain.User;
import com.jobtrack.support.IntegrationTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication and session integration tests, against a real PostgreSQL database.
 *
 * <p>Covers the contract a client depends on: what a 401 looks like (and why), how refresh rotation
 * behaves under reuse, and that password handling is BCrypt-only.</p>
 */
class AuthApiIT extends IntegrationTestSupport {

    private static final String ISSUER = "jobtrack-api";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------- registration

    @Test
    @DisplayName("POST /api/auth/register returns 201 with a token pair and never the password")
    void registersANewUser() throws Exception {
        JsonNode body = register(email, "Password123", "Asha Verma");

        assertThat(body.path("accessToken").asText()).isNotBlank();
        assertThat(body.path("refreshToken").asText()).isNotBlank();
        assertThat(body.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.path("expiresIn").asLong()).isEqualTo(Duration.ofMinutes(30).toSeconds());
        assertThat(body.path("user").path("email").asText()).isEqualTo(email);
        assertThat(body.path("user").path("role").asText()).isEqualTo("USER");
        assertThat(body.toString()).doesNotContain("Password123");
        assertThat(body.path("user").has("passwordHash")).isFalse();
    }

    @Test
    @DisplayName("the stored password is a BCrypt hash, salted per user")
    void storesABcryptHash() throws Exception {
        String sharedPassword = "SamePassword1";
        String first = uniqueEmail();
        String second = uniqueEmail();

        register(first, sharedPassword, "One");
        register(second, sharedPassword, "Two");

        User one = userRepository.findByEmailIgnoreCase(first).orElseThrow();
        User two = userRepository.findByEmailIgnoreCase(second).orElseThrow();

        assertThat(one.getPasswordHash()).isNotEqualTo(sharedPassword);
        assertThat(one.getPasswordHash()).startsWith("$2");
        assertThat(one.getPasswordHash()).isNotEqualTo(two.getPasswordHash());
        assertThat(passwordEncoder.matches(sharedPassword, one.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("a duplicate email is a 409, not a 500 or a silent overwrite")
    void rejectsDuplicateEmail() throws Exception {
        register(email, "Password123", "First");

        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("name", "Second", "email", email, "password", "Password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("weak input is rejected with 400 and a per-field explanation")
    void validatesRegistrationInput() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("name", "A", "email", "not-an-email", "password", "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("a password without a digit is rejected")
    void rejectsPasswordWithoutDigit() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("name", "Asha Verma", "email", email, "password", "onlyletters")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    // -------------------------------------------------------------------- login

    @Test
    @DisplayName("POST /api/auth/login returns a session for valid credentials")
    void logsInWithValidCredentials() throws Exception {
        register(email, "Password123", "Asha Verma");

        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", "Password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    @DisplayName("wrong password and unknown email produce the same 401, so accounts cannot be enumerated")
    void loginFailuresAreIndistinguishable() throws Exception {
        register(email, "Password123", "Asha Verma");

        String wrongPassword = mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("email", email, "password", "WrongPassword1")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("email", "nobody@jobtrack.test", "password", "Password123")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(readJson(wrongPassword).path("message").asText())
                .isEqualTo(readJson(unknownEmail).path("message").asText());
        assertThat(readJson(wrongPassword).path("details").path("reason").asText())
                .isEqualTo("invalid_credentials");
    }

    // ------------------------------------------------------------ token handling

    @Test
    @DisplayName("a request without a token is 401 with reason missing_credentials")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.details.reason").value("missing_credentials"));
    }

    @Test
    @DisplayName("a malformed or tampered token is 401 with reason token_invalid")
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.details.reason").value("token_invalid"));

        String token = register().path("accessToken").asText();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.details.reason").value("token_invalid"));
    }

    @Test
    @DisplayName("an expired token is 401 with reason token_expired so the client knows to refresh")
    void rejectsExpiredToken() throws Exception {
        Date now = new Date();
        String expired = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("1")
                .issuer(ISSUER)
                .audience().add(ISSUER).and()
                .claim("typ", "access")
                .issuedAt(new Date(now.getTime() - 7200_000))
                .expiration(new Date(now.getTime() - 3600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.details.reason").value("token_expired"));
    }

    @Test
    @DisplayName("a token signed for another service is rejected even though the secret matches")
    void rejectsTokenWithWrongIssuer() throws Exception {
        Date now = new Date();
        String foreign = Jwts.builder()
                .subject("1")
                .issuer("some-other-api")
                .audience().add("some-other-api").and()
                .claim("typ", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.details.reason").value("token_invalid"));
    }

    @Test
    @DisplayName("GET /api/users/me returns the caller's own profile")
    void returnsOwnProfile() throws Exception {
        String token = registerAndReturnToken();

        mockMvc.perform(asUser(get("/api/users/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Test Candidate"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ----------------------------------------------------------- refresh rotation

    @Test
    @DisplayName("refresh rotates the pair, and reusing the old refresh token revokes the whole family")
    void rotatesRefreshTokensAndDetectsReuse() throws Exception {
        JsonNode session = register(email, "Password123", "Asha Verma");
        String first = session.path("refreshToken").asText();

        JsonNode rotated = readJson(mockMvc.perform(json(post("/api/auth/refresh"),
                        Map.of("refreshToken", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        String second = rotated.path("refreshToken").asText();

        assertThat(second).isNotEqualTo(first);

        // Replaying the consumed token is either theft or a bug - both must kill the family.
        mockMvc.perform(json(post("/api/auth/refresh"), Map.of("refreshToken", first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.details.reason").value("token_reused"));

        mockMvc.perform(json(post("/api/auth/refresh"), Map.of("refreshToken", second)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.details.reason").value("token_reused"));
    }

    @Test
    @DisplayName("an unknown refresh token is rejected without creating a session")
    void rejectsUnknownRefreshToken() throws Exception {
        mockMvc.perform(json(post("/api/auth/refresh"), Map.of("refreshToken", "never-issued")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout revokes the refresh token; the access token still expires on its own clock")
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode session = register(email, "Password123", "Asha Verma");
        String refreshToken = session.path("refreshToken").asText();
        String accessToken = session.path("accessToken").asText();

        mockMvc.perform(asUser(json(post("/api/auth/logout"), Map.of("refreshToken", refreshToken)),
                        accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(json(post("/api/auth/refresh"), Map.of("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout with an unknown token is still 204 - logging out must never fail")
    void logoutIsIdempotent() throws Exception {
        String token = registerAndReturnToken();

        mockMvc.perform(asUser(json(post("/api/auth/logout"), Map.of("refreshToken", "bogus")), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(json(post("/api/auth/logout"), Map.of("refreshToken", "bogus")))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ profile changes

    @Test
    @DisplayName("changing the password revokes every refresh token, signing out other devices")
    void passwordChangeRevokesSessions() throws Exception {
        JsonNode session = register(email, "Password123", "Asha Verma");
        String accessToken = session.path("accessToken").asText();
        String refreshToken = session.path("refreshToken").asText();

        mockMvc.perform(asUser(json(post("/api/users/me/password"),
                        Map.of("currentPassword", "Password123", "newPassword", "NewPassword9")),
                        accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(json(post("/api/auth/refresh"), Map.of("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", "NewPassword9")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("changing the password requires the current one")
    void passwordChangeRequiresCurrentPassword() throws Exception {
        String accessToken = registerAndReturnToken();

        mockMvc.perform(asUser(json(post("/api/users/me/password"),
                        Map.of("currentPassword", "NotMyPassword1", "newPassword", "NewPassword9")),
                        accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the profile can update name and time zone, and rejects an unknown zone")
    void updatesProfile() throws Exception {
        String accessToken = registerAndReturnToken();

        mockMvc.perform(asUser(json(patch("/api/users/me"),
                        Map.of("name", "Asha V", "timezone", "Asia/Kolkata", "emailRemindersEnabled", true)),
                        accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha V"))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.emailRemindersEnabled").value(true));

        mockMvc.perform(asUser(json(patch("/api/users/me"),
                        Map.of("name", "Asha V", "timezone", "Mars/Olympus_Mons")),
                        accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the API docs are reachable so the Swagger 'try it out' flow works")
    void openApiDocumentIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/applications']").exists());
    }
}
