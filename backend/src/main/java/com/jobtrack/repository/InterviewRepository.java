package com.jobtrack.repository;

import com.jobtrack.domain.Interview;
import com.jobtrack.domain.InterviewOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link Interview}.
 *
 * <p>Ownership is expressed through the owning application
 * ({@code i.application.userId = :userId}) - there is no way to read or write an interview round
 * that belongs to somebody else's application.</p>
 */
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    @Query("""
            select i from Interview i join fetch i.application a
            where i.id = :id and a.userId = :userId
            """)
    Optional<Interview> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            select i from Interview i join fetch i.application a
            where a.id = :applicationId and a.userId = :userId
            order by i.scheduledAt asc
            """)
    List<Interview> findByApplicationIdAndUserId(@Param("applicationId") Long applicationId,
                                                 @Param("userId") Long userId);

    @Query("""
            select i from Interview i join fetch i.application a
            where a.userId = :userId
              and i.scheduledAt >= :now
              and i.outcome in :openOutcomes
            order by i.scheduledAt asc, a.company asc
            """)
    List<Interview> findUpcoming(@Param("userId") Long userId,
                                 @Param("now") Instant now,
                                 @Param("openOutcomes") Collection<InterviewOutcome> openOutcomes,
                                 Pageable pageable);

    @Query("""
            select i from Interview i join fetch i.application a
            where a.userId = :userId
            order by i.scheduledAt desc
            """)
    Page<Interview> findAllForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select i from Interview i join fetch i.application a
            where a.userId = :userId and i.scheduledAt > :now and i.outcome in :openOutcomes
            order by i.scheduledAt asc
            """)
    Page<Interview> findUpcomingPage(@Param("userId") Long userId,
                                     @Param("now") Instant now,
                                     @Param("openOutcomes") Collection<InterviewOutcome> openOutcomes,
                                     Pageable pageable);

    @Query("""
            select i from Interview i join fetch i.application a
            where a.userId = :userId and (i.scheduledAt <= :now or i.outcome not in :openOutcomes)
            order by i.scheduledAt desc
            """)
    Page<Interview> findPastPage(@Param("userId") Long userId,
                                 @Param("now") Instant now,
                                 @Param("openOutcomes") Collection<InterviewOutcome> openOutcomes,
                                 Pageable pageable);

    @Query("select count(i) from Interview i where i.application.userId = :userId")
    long countForUser(@Param("userId") Long userId);

    @Query("""
            select count(i) from Interview i
            where i.application.userId = :userId
              and i.scheduledAt >= :now
              and i.outcome in :openOutcomes
            """)
    long countUpcoming(@Param("userId") Long userId,
                       @Param("now") Instant now,
                       @Param("openOutcomes") Collection<InterviewOutcome> openOutcomes);

    @Query("""
            select count(i) from Interview i
            where i.application.userId = :userId
              and i.outcome = :outcome
            """)
    long countByOutcome(@Param("userId") Long userId, @Param("outcome") InterviewOutcome outcome);

    /** Interview rounds per calendar month, used by the trend chart. */
    @Query("""
            select extract(year from i.scheduledAt), extract(month from i.scheduledAt), count(i)
            from Interview i
            where i.application.userId = :userId
              and i.scheduledAt between :from and :to
            group by extract(year from i.scheduledAt), extract(month from i.scheduledAt)
            """)
    List<Object[]> monthlyInterviews(@Param("userId") Long userId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);

    /**
     * Rounds of one user that still need an outcome and start inside the given window.
     * Used by the reminder scheduler; {@code join fetch} keeps the owner check off the N+1 path.
     */
    @Query("""
            select i from Interview i join fetch i.application a
            where a.userId = :userId
              and i.scheduledAt between :from and :to
              and i.outcome = :pending
            order by i.scheduledAt asc
            """)
    List<Interview> findPendingForUserBetween(@Param("userId") Long userId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to,
                                              @Param("pending") InterviewOutcome pending);
}
