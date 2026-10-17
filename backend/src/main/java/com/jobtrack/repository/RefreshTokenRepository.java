package com.jobtrack.repository;

import com.jobtrack.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link RefreshToken}.
 *
 * <p>Only hashes are stored, and rotation marks the previous row revoked + links it to its
 * replacement, which is what makes reuse detection possible.</p>
 *
 * <p>Every bulk update sets {@code clearAutomatically}: a {@code @Modifying} JPQL statement goes
 * straight to the database and bypasses the persistence context, so any {@code RefreshToken} entity
 * already loaded in that context would keep its pre-update state. For a revocation that is a security
 * bug waiting to happen - a revoked token would still look valid to a later read in the same context
 * (one long-lived transaction, a retry inside the same request, an integration test).</p>
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    long countByRevokedAtIsNullAndExpiresAtAfter(Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Reuse detection: presenting an already-rotated token revokes every token in its family. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :olderThan or (t.revokedAt is not null and t.revokedAt < :olderThan)")
    int deleteExpired(@Param("olderThan") Instant olderThan);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
