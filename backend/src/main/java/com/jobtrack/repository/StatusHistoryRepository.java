package com.jobtrack.repository;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Append-only audit trail of status transitions.
 *
 * <p>The funnel is computed from this table (stages an application <em>reached</em>) rather than from
 * the current status alone, so an application that was interviewed and later rejected still counts
 * in the interview stage.</p>
 */
public interface StatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByChangedAtAscIdAsc(Long applicationId);

    long countByApplicationId(Long applicationId);

    /**
     * Applications that reached each status, scoped to one user.
     * Returns {@code Object[]{ApplicationStatus toStatus, Long applications}}.
     */
    @Query("""
            select h.toStatus, count(distinct h.applicationId)
            from ApplicationStatusHistory h
            where h.applicationId in (select a.id from JobApplication a where a.userId = :userId)
            group by h.toStatus
            """)
    List<Object[]> countReachedStatuses(@Param("userId") Long userId);

    /**
     * Conversion funnel: applications that reached each stage, cumulative.
     *
     * <p>One native aggregate (no row loading). {@code count(distinct case when ...)} counts an
     * application once per stage even if it visited that stage several times, and reading the audit
     * trail means an application rejected after interviewing still counts in the interview stage.</p>
     *
     * @return single row: {@code [applied, assessment, interview, offer]}
     */
    @Query(value = """
            select
              count(distinct case when h.to_status in ('APPLIED','ASSESSMENT','INTERVIEW','OFFER')
                                  then h.application_id end),
              count(distinct case when h.to_status in ('ASSESSMENT','INTERVIEW','OFFER')
                                  then h.application_id end),
              count(distinct case when h.to_status in ('INTERVIEW','OFFER')
                                  then h.application_id end),
              count(distinct case when h.to_status = 'OFFER' then h.application_id end)
            from application_status_history h
            join job_applications a on a.id = h.application_id
            where a.user_id = :userId
            """, nativeQuery = true)
    List<Object[]> funnelCountsRaw(@Param("userId") Long userId);

    /** Convenience wrapper returning the four funnel counters as longs. */
    default long[] funnelCounts(Long userId) {
        List<Object[]> rows = funnelCountsRaw(userId);
        if (rows.isEmpty() || rows.getFirst() == null) {
            return new long[]{0, 0, 0, 0};
        }
        Object[] row = rows.getFirst();
        long[] counts = new long[4];
        for (int i = 0; i < 4 && i < row.length; i++) {
            counts[i] = row[i] == null ? 0 : ((Number) row[i]).longValue();
        }
        return counts;
    }

    /**
     * Status entries per calendar month for one target status - used for the offer / rejection lines
     * of the trend chart.
     * Returns {@code Object[]{Number year, Number month, Long count}}.
     */
    @Query("""
            select extract(year from h.changedAt), extract(month from h.changedAt), count(h)
            from ApplicationStatusHistory h
            where h.toStatus = :status
              and h.changedAt between :from and :to
              and h.applicationId in (select a.id from JobApplication a where a.userId = :userId)
            group by extract(year from h.changedAt), extract(month from h.changedAt)
            """)
    List<Object[]> monthlyStatusCounts(@Param("userId") Long userId,
                                       @Param("status") ApplicationStatus status,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    /** How long an application stayed in each status, newest first (detail page timeline). */
    @Query("""
            select h from ApplicationStatusHistory h
            where h.applicationId in (select a.id from JobApplication a where a.userId = :userId)
            order by h.changedAt desc
            """)
    List<ApplicationStatusHistory> findRecentForUser(@Param("userId") Long userId,
                                                     org.springframework.data.domain.Pageable pageable);
}
