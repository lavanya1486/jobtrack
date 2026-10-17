package com.jobtrack.security;

import com.jobtrack.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JWT issuing and validation.
 *
 * <p>The security properties that matter are asserted individually: signature, expiry, issuer,
 * audience and the {@code typ} claim that separates access tokens from anything else signed with the
 * same key.</p>
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-definitely-long-enough-for-hs256";
    private static final String OTHER_SECRET = "a-completely-different-secret-value-for-negative-tests";

    private JwtService service(String secret, Duration ttl) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(secret, ttl, Duration.ofDays(7), "jobtrack-api"),
                new AppProperties.Cors(java.util.List.of("http://localhost:5173")),
                new AppProperties.Reminders(true, "-", Duration.ofHours(48), Duration.ofHours(24),
                        Duration.ofDays(7), 14, false, Duration.ofHours(24), "-", "-",
                        "no-reply@jobtrack.local"),
                new AppProperties.Seed(false, "demo@jobtrack.local", "Demo1234!",
                        "admin@jobtrack.local", "Admin1234!"));
        return new JwtService(properties, new MockEnvironment());
    }

    private JwtService service() {
        return service(SECRET, Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("a generated token round-trips into the same identity and claims")
    void roundTripsClaims() {
        JwtService jwt = service();

        String token = jwt.generateAccessToken(42L, "asha@example.com", "Asha Verma", "USER");
        Claims claims = jwt.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email")).isEqualTo("asha@example.com");
        assertThat(claims.get("name")).isEqualTo("Asha Verma");
        assertThat(claims.get("role")).isEqualTo("USER");
        assertThat(claims.get("typ")).isEqualTo("access");
        assertThat(claims.getIssuer()).isEqualTo("jobtrack-api");
        assertThat(claims.getAudience()).contains("jobtrack-api");
        assertThat(claims.getId()).isNotBlank();
        assertThat(jwt.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void expiryFollowsTheConfiguredTtl() {
        JwtService jwt = service(SECRET, Duration.ofMinutes(30));

        String token = jwt.generateAccessToken(1L, "a@b.c", "A", "USER");
        Claims claims = jwt.parse(token);

        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isBetween(Duration.ofMinutes(29), Duration.ofMinutes(31));
    }

    @Test
    @DisplayName("an expired token is reported as expired, so the client can refresh instead of re-login")
    void rejectsExpiredToken() {
        // Negative TTL puts `exp` in the past, outside the 30 second clock skew allowance.
        JwtService issuer = service(SECRET, Duration.ofSeconds(-120));
        String expired = issuer.generateAccessToken(7L, "asha@example.com", "Asha", "USER");

        JwtService validator = service();

        assertThatThrownBy(() -> validator.parse(expired)).isInstanceOf(ExpiredJwtException.class);
        assertThat(validator.isExpired(catchJwt(() -> validator.parse(expired)))).isTrue();
        assertThat(validator.extractUserId(expired)).isNull();
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void rejectsForeignSignature() {
        String token = service(OTHER_SECRET, Duration.ofMinutes(30))
                .generateAccessToken(1L, "asha@example.com", "Asha", "USER");

        JwtService validator = service();

        assertThatThrownBy(() -> validator.parse(token))
                .isInstanceOf(SignatureException.class);
        assertThat(validator.extractUserId(token)).isNull();
    }

    @Test
    @DisplayName("a tampered payload invalidates the signature")
    void rejectsTamperedToken() {
        JwtService jwt = service();
        String token = jwt.generateAccessToken(1L, "asha@example.com", "Asha", "USER");

        String[] parts = token.split("\\.");
        // Flip the subject from 1 to 999 inside the payload and keep the original signature.
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String tamperedPayload = payload.replace("\"sub\":\"1\"", "\"sub\":\"999\"");
        String reencoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes());
        String tampered = parts[0] + "." + reencoded + "." + parts[2];

        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(JwtException.class);
        assertThat(jwt.extractUserId(tampered)).isNull();
    }

    @Test
    @DisplayName("garbage input is handled without leaking an exception to the caller")
    void handlesGarbage() {
        JwtService jwt = service();

        assertThat(jwt.extractUserId("not-a-jwt")).isNull();
        assertThat(jwt.extractUserId("")).isNull();
        assertThatThrownBy(() -> jwt.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a secret shorter than 256 bits fails fast instead of signing weakly")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> service("too-short", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("without a secret, production refuses to start while development gets an ephemeral key")
    void missingSecretBehaviourDependsOnProfile() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("", Duration.ofMinutes(30), Duration.ofDays(7), "jobtrack-api"),
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Reminders(false, "-", Duration.ofHours(48), Duration.ofHours(24),
                        Duration.ofDays(7), 14, false, Duration.ofHours(24), "-", "-", "x@y.z"),
                new AppProperties.Seed(false, "a", "b", "c", "d"));

        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtService(properties, production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET");

        JwtService development = new JwtService(properties, new MockEnvironment());
        assertThat(development.generateAccessToken(1L, "a@b.c", "A", "USER")).isNotBlank();
    }

    private static JwtException catchJwt(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected a JwtException");
        } catch (JwtException exception) {
            return exception;
        }
    }

    @Test
    @DisplayName("tokens are always signed with HS256, whatever the configured key length")
    void alwaysSignsWithHs256() {
        // jjwt would otherwise pick HS384/HS512 for longer keys, changing the header from under us.
        JwtService jwt = service(
                "a-much-longer-secret-value-that-would-otherwise-select-a-stronger-algorithm",
                Duration.ofMinutes(5));

        String token = jwt.generateAccessToken(7L, "asha@example.com", "Asha Verma", "USER");
        String header = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(header).contains("\"alg\":\"HS256\"");
        assertThat(jwt.parse(token).getSubject()).isEqualTo("7");
    }
}
