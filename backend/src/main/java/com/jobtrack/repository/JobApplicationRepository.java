package com.jobtrack.repository;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.JobApplication;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link JobApplication}.
 *
 * <p>Security rule: every find/count/aggregate method takes the owning {@code userId}, so ownership
 * is enforced in the data-access layer rather than by filtering results after the fact.
 * {@link JpaSpecificationExecutor} backs the dynamic search/filter/sort/paginate query.</p>
 */
public interface JobApplicationRepository
        extends JpaRepository<JobApplication, Long>, JpaSpecificationExecutor<JobApplication> {

    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Platform-wide counter used by the admin dashboard. */
    long countByStatusNot(ApplicationStatus status);

    List<JobApplication> findByUserIdOrderByCreatedAtDesc(Long userId);

    // ---------------------------------------------------------------- analytics ----

    /**
     * Single-pass KPI aggregate for one user.
     *
     * <p>{@code submitted} (status != SAVED) is the denominator for the response, interview and
     * offer rates. Drafts are excluded so a user who saves ten jobs but submits two is not shown a
     * 20% response rate for applications nobody ever saw.</p>
     */
    @Query("""
            select count(a),
                   sum(case when a.status <> :draft then 1 else 0 end),
                   sum(case when a.status = :draft then 1 else 0 end),
                   sum(case when a.status in :activeStatuses then 1 else 0 end),
                   sum(case when a.status in :closedStatuses then 1 else 0 end),
                   sum(case when a.firstResponseAt is not null then 1 else 0 end),
                   sum(case when a.status = :offer then 1 else 0 end),
                   sum(case when a.status = :rejected then 1 else 0 end),
                   sum(case when a.status = :withdrawn then 1 else 0 end),
                   sum(case when a.deadline is not null and a.deadline < :today
                             and a.status in :activeStatuses then 1 else 0 end),
                   sum(case when a.nextActionDate is not null and a.nextActionDate <= :today
                             and a.status in :activeStatuses then 1 else 0 end),
                   avg(cast(a.expectedSalaryMax as double))
            from JobApplication a
            where a.userId = :userId
            """)
    List<Object[]> aggregateStatsRaw(@Param("userId") Long userId,
                                     @Param("draft") ApplicationStatus draft,
                                     @Param("activeStatuses") Collection<ApplicationStatus> activeStatuses,
                                     @Param("closedStatuses") Collection<ApplicationStatus> closedStatuses,
                                     @Param("offer") ApplicationStatus offer,
                                     @Param("rejected") ApplicationStatus rejected,
                                     @Param("withdrawn") ApplicationStatus withdrawn,
                                     @Param("today") LocalDate today);

    /** Type-safe wrapper around {@link #aggregateStatsRaw}; an aggregate always returns one row. */
    default ApplicationStats aggregateStats(Long userId,
                                            ApplicationStatus draft,
                                            Collection<ApplicationStatus> activeStatuses,
                                            Collection<ApplicationStatus> closedStatuses,
                                            ApplicationStatus offer,
                                            ApplicationStatus rejected,
                                            ApplicationStatus withdrawn,
                                            LocalDate today) {
        List<Object[]> rows = aggregateStatsRaw(userId, draft, activeStatuses, closedStatuses,
                offer, rejected, withdrawn, today);
        Object[] row = rows.isEmpty() ? new Object[12] : rows.getFirst();
        return new ApplicationStats(
                asLong(row, 0), asLong(row, 1), asLong(row, 2), asLong(row, 3), asLong(row, 4),
                asLong(row, 5), asLong(row, 6), asLong(row, 7), asLong(row, 8), asLong(row, 9),
                asLong(row, 10), asDouble(row, 11));
    }

    private static long asLong(Object[] row, int index) {
        Number value = number(row, index);
        return value == null ? 0L : value.longValue();
    }

    private static Double asDouble(Object[] row, int index) {
        Number value = number(row, index);
        return value == null ? null : value.doubleValue();
    }

    /** {@code sum(...)} over zero rows is SQL NULL, so every position is read defensively. */
    private static Number number(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return null;
        }
        return (Number) row[index];
    }

    /**
     * Applications that reached the interview stage: current status is INTERVIEW/OFFER
     * <em>or</em> at least one interview round is recorded. The {@code exists} keeps an application
     * from being counted twice when both conditions hold.
     */
    @Query("""
            select count(a)
            from JobApplication a
            where a.userId = :userId
              and (a.status in :interviewStatuses
                   or exists (select 1 from Interview i where i.application.id = a.id))
            """)
    long countReachedInterview(@Param("userId") Long userId,
                               @Param("interviewStatuses") Collection<ApplicationStatus> interviewStatuses);

    /** Status distribution: {@code Object[]{ApplicationStatus status, Long count}}. */
    @Query("""
            select a.status, count(a)
            from JobApplication a
            where a.userId = :userId
            group by a.status
            order by count(a) desc, a.status asc
            """)
    List<Object[]> countGroupedByStatus(@Param("userId") Long userId);

    /**
     * Applications submitted per calendar month.
     * Returns {@code Object[]{Number year, Number month, Long count}}.
     */
    @Query("""
            select extract(year from a.appliedAt), extract(month from a.appliedAt), count(a)
            from JobApplication a
            where a.userId = :userId
              and a.appliedAt is not null
              and a.appliedAt between :from and :to
            group by extract(year from a.appliedAt), extract(month from a.appliedAt)
            """)
    List<Object[]> monthlyApplications(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /** Employer responses per calendar month, keyed on when the response was recorded. */
    @Query("""
            select extract(year from a.firstResponseAt), extract(month from a.firstResponseAt), count(a)
            from JobApplication a
            where a.userId = :userId
              and a.firstResponseAt is not null
              and a.firstResponseAt between :from and :to
            group by extract(year from a.firstResponseAt), extract(month from a.firstResponseAt)
            """)
    List<Object[]> monthlyResponses(@Param("userId") Long userId,
                                    @Param("from") java.time.Instant from,
                                    @Param("to") java.time.Instant to);

    /**
     * Top companies by application count, with interview / offer counters for context.
     * Returns {@code Object[]{String company, Long applications, Long interviews, Long offers}}.
     */
    @Query("""
            select a.company,
                   count(a),
                   sum(case when a.status in :interviewStatuses then 1 else 0 end),
                   sum(case when a.status = :offer then 1 else 0 end)
            from JobApplication a
            where a.userId = :userId
            group by a.company
            order by count(a) desc, a.company asc
            """)
    List<Object[]> topCompanies(@Param("userId") Long userId,
                                @Param("interviewStatuses") Collection<ApplicationStatus> interviewStatuses,
                                @Param("offer") ApplicationStatus offer,
                                Pageable pageable);

    /**
     * Average number of days between submitting an application and the first employer response.
     * Native query because the calculation needs Postgres interval arithmetic.
     */
    @Query(value = """
            select avg(extract(epoch from (a.first_response_at - a.applied_at::timestamptz)) / 86400.0)
            from job_applications a
            where a.user_id = :userId
              and a.first_response_at is not null
              and a.applied_at is not null
            """, nativeQuery = true)
    Double averageDaysToFirstResponse(@Param("userId") Long userId);

    // ------------------------------------------------------------- dashboard lists ----

    /**
     * Open applications whose deadline is on or before {@code to}, soonest first.
     * Overdue deadlines sort to the top, which is what the dashboard wants.
     */
    @Query("""
            select a
            from JobApplication a
            where a.userId = :userId
              and a.deadline is not null
              and a.deadline <= :to
              and a.status in :activeStatuses
            order by a.deadline asc, a.company asc
            """)
    List<JobApplication> findWithDeadlineUpTo(@Param("userId") Long userId,
                                              @Param("to") LocalDate to,
                                              @Param("activeStatuses") Collection<ApplicationStatus> activeStatuses,
                                              Pageable pageable);

    /** Open applications whose next action is due on or before {@code today}. */
    @Query("""
            select a
            from JobApplication a
            where a.userId = :userId
              and a.nextActionDate is not null
              and a.nextActionDate <= :today
              and a.status in :activeStatuses
            order by a.nextActionDate asc, a.company asc
            """)
    List<JobApplication> findWithNextActionDue(@Param("userId") Long userId,
                                               @Param("today") LocalDate today,
                                               @Param("activeStatuses") Collection<ApplicationStatus> activeStatuses,
                                               Pageable pageable);

    /** Applications used by the reminder scheduler: open, with a deadline or a next action. */
    @Query("""
            select a
            from JobApplication a
            where a.userId = :userId
              and a.status in :activeStatuses
              and (a.deadline is not null or a.nextActionDate is not null)
            """)
    List<JobApplication> findActiveWithDates(@Param("userId") Long userId,
                                             @Param("activeStatuses") Collection<ApplicationStatus> activeStatuses);

    /**
     * Distinct lower-cased tag values of a user, for the filter auto-complete.
     * Returns the raw tag strings; de-duplicated in the service.
     */
    @Query(value = """
            select distinct lower(t.tag)
            from application_tags t
            join job_applications a on a.id = t.application_id
            where a.user_id = :userId
            order by 1
            """, nativeQuery = true)
    List<String> findDistinctTags(@Param("userId") Long userId);

    /** Distinct locations of a user, for the filter auto-complete. */
    @Query("""
            select distinct a.location
            from JobApplication a
            where a.userId = :userId and a.location is not null and a.location <> ''
            order by a.location asc
            """)
    List<String> findDistinctLocations(@Param("userId") Long userId);

    /** Distinct companies of a user, for the filter auto-complete. */
    @Query("""
            select distinct a.company
            from JobApplication a
            where a.userId = :userId
            order by a.company asc
            """)
    List<String> findDistinctCompanies(@Param("userId") Long userId);
}
