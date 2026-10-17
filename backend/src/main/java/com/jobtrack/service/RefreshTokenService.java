package com.jobtrack.service;

import com.jobtrack.config.AppProperties;
import com.jobtrack.domain.RefreshToken;
import com.jobtrack.domain.User;
import com.jobtrack.exception.UnauthorizedException;
import com.jobtrack.repository.RefreshTokenRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.RequestMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Issues, rotates and revokes opaque refresh tokens.
 *
 * <p><b>Rotation with reuse detection.</b> Every refresh consumes the presented token (marks it
 * revoked, links it to its replacement) and issues a new one in the same <em>family</em>. If a token
 * that has already been consumed is presented again, the whole family is revoked: that pattern means
 * the token leaked, and an attacker or the legitimate user is now holding a dead session.</p>
 *
 * <p><b>Storage.</b> Only the SHA-256 hash is persisted, so a database leak cannot be replayed. The
 * raw token is a 256-bit URL-safe random string - it carries no claims and cannot be forged.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    /** Creates a new token family for a fresh login. */
    @Transactional
    public String issue(User user, RequestMetadata metadata) {
        return persist(user.getId(), UUID.randomUUID(), metadata);
    }

    /**
     * Exchanges a valid refresh token for a new one.
     *
     * @return map with {@code refreshToken} (raw new token) and {@code userId}
     * @throws UnauthorizedException when the token is unknown, expired, revoked or reused
     */
    /**
     * {@code noRollbackFor} matters here: the reuse / expiry branches write a security-relevant
     * revocation and then throw. Without it the throw would roll the revocation back and the stolen
     * token family would stay usable.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RotationResult rotate(String rawToken, RequestMetadata metadata) {
        String hash = hash(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token",
                        Map.of("reason", "unknown_token")));

        Instant now = Instant.now();

        if (stored.getRevokedAt() != null) {
            // Reuse detection: a rotated token must never be presented again.
            int revoked = refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {} - revoked {} tokens in family {}",
                    stored.getUserId(), revoked, stored.getFamilyId());
            throw new UnauthorizedException("Refresh token has already been used; the session was revoked",
                    Map.of("reason", "token_reused"));
        }

        if (stored.isExpired(now)) {
            stored.revoke(now);
            throw new UnauthorizedException("Refresh token has expired", Map.of("reason", "token_expired"));
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists",
                        Map.of("reason", "user_not_found")));
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            throw new UnauthorizedException("Account is disabled", Map.of("reason", "account_disabled"));
        }

        String newRawToken = persist(user.getId(), stored.getFamilyId(), metadata);
        stored.revoke(now);
        stored.setReplacedByTokenHash(hash(newRawToken));

        return new RotationResult(user, newRawToken);
    }

    /** Revokes a single refresh token (logout). Unknown tokens are ignored - logout is idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.revoke(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    /** Signs the user out everywhere (password change, admin action). */
    @Transactional
    public int revokeAllForUser(Long userId) {
        return refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    /** Housekeeping for the scheduler. */
    @Transactional
    public int purgeExpired() {
        return refreshTokenRepository.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
    }

    public Duration ttl() {
        return appProperties.jwt().refreshTokenTtl();
    }

    public long countActive() {
        return refreshTokenRepository.countByRevokedAtIsNullAndExpiresAtAfter(Instant.now());
    }

    private String persist(Long userId, UUID familyId, RequestMetadata metadata) {
        String rawToken = generateRawToken();
        RequestMetadata safe = metadata == null ? RequestMetadata.unknown() : metadata.truncated();

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(rawToken));
        token.setFamilyId(familyId);
        token.setExpiresAt(Instant.now().plus(appProperties.jwt().refreshTokenTtl()));
        token.setUserAgent(safe.userAgent());
        token.setIpAddress(safe.ipAddress());
        refreshTokenRepository.save(token);
        return rawToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest (64 chars) - the only form of the token that is ever stored. */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }

    /** Result of a successful rotation. */
    public record RotationResult(User user, String refreshToken) {
    }
}
