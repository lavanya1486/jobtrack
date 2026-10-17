package com.jobtrack.security;

import com.jobtrack.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and validates HS256-signed access tokens.
 *
 * <p>Design decisions worth defending in an interview:</p>
 * <ul>
 *   <li><b>Short-lived access tokens + rotating refresh tokens.</b> An access token cannot be
 *       revoked once issued, so it lives for minutes; the revocable long-lived credential is the
 *       opaque refresh token stored (hashed) in the database.</li>
 *   <li><b>Issuer and audience are validated</b>, so a token minted for another service is rejected
 *       even if it shares the secret.</li>
 *   <li><b>The user id is the subject</b>, and the filter re-loads the account, which means a
 *       disabled account or a changed role takes effect immediately instead of at token expiry.</li>
 *   <li><b>The secret has no default</b>: a missing or short secret fails fast at start-up.</li>
 * </ul>
 */
@Service
@Slf4j
public class JwtService {

    /** Access tokens are identified by {@code typ=access}; refresh tokens are opaque, not JWTs. */
    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_JTI = "jti";
    static final String TYPE_ACCESS = "access";

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final String issuer;

    public JwtService(AppProperties properties, Environment environment) {
        String secret = properties.jwt().secret();

        if (!StringUtils.hasText(secret)) {
            if (isProduction(environment)) {
                throw new IllegalStateException(
                        "app.jwt.secret is not set. Provide APP_JWT_SECRET (>= 32 characters) as an "
                                + "environment variable - refusing to start in production without one.");
            }
            // Development convenience: an ephemeral secret so `mvn spring-boot:run` works out of the
            // box. Tokens issued before a restart stop validating, which is the intended behaviour.
            secret = randomSecret();
            log.warn("app.jwt.secret is not set - generated an ephemeral development secret. "
                    + "Every restart invalidates existing tokens. Set APP_JWT_SECRET before deploying.");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256 (got "
                            + keyBytes.length + ").");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
        this.issuer = properties.jwt().issuer();
    }

    private static boolean isProduction(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                        || profile.equalsIgnoreCase("production"));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Mints an access token for a user.
     *
     * @return the compact JWT
     */
    public String generateAccessToken(Long userId, String email, String name, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .audience().add(issuer).and()
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * Verifies signature, expiry, issuer and token type.
     *
     * @throws ExpiredJwtException when the token is past its {@code exp}
     * @throws JwtException        for any other validation failure (bad signature, malformed, wrong issuer)
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(issuer)
                .require(CLAIM_TYPE, TYPE_ACCESS)
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Convenience wrapper used by the filter: {@code null} when the token is unusable. */
    public Long extractUserId(String token) {
        try {
            return Long.valueOf(parse(token).getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected access token: {}", e.getMessage());
            return null;
        }
    }

    public boolean isExpired(JwtException exception) {
        return exception instanceof ExpiredJwtException;
    }

    /** Extra claims, exposed for tests and diagnostics. */
    public Map<String, Object> claimsOf(String token) {
        return parse(token);
    }
}
