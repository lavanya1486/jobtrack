package com.jobtrack.repository;

import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.ReminderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link Reminder}.
 *
 * <p>Reminders are stored with the owning {@code userId} denormalised (in addition to the optional
 * application link) so "my open reminders" never has to join through applications.</p>
 */
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    Optional<Reminder> findByIdAndUserId(Long id, Long userId);

    List<Reminder> findByApplicationIdAndUserIdOrderByDueAtAsc(Long applicationId, Long userId);

    /**
     * Duplicate prevention for the scheduler: does an open reminder with this dedupe key exist?
     * The partial unique index in the schema is the final guard; this check keeps the normal path
     * free of constraint violations.
     */
    boolean existsByUserIdAndDedupeKeyAndCompletedAtIsNull(Long userId, String dedupeKey);

    /** The open reminder for a dedupe key, if any (used to move a reminder when a date changes). */
    java.util.Optional<Reminder> findFirstByUserIdAndDedupeKeyAndCompletedAtIsNull(Long userId, String dedupeKey);

    @Query("""
            select r from Reminder r
            where r.userId = :userId and r.completedAt is null
            order by r.dueAt asc, r.id asc
            """)
    Page<Reminder> findOpen(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select r from Reminder r
            where r.userId = :userId and r.completedAt is null and r.dueAt < :now
            order by r.dueAt asc
            """)
    List<Reminder> findOverdue(@Param("userId") Long userId, @Param("now") Instant now, Pageable pageable);

    @Query("""
            select r from Reminder r
            where r.userId = :userId and r.completedAt is null and r.dueAt < :now
            order by r.dueAt asc
            """)
    Page<Reminder> findOverduePage(@Param("userId") Long userId, @Param("now") Instant now,
                                   Pageable pageable);

    @Query("""
            select r from Reminder r
            where r.userId = :userId and r.completedAt is null and r.dueAt between :from and :to
            order by r.dueAt asc
            """)
    Page<Reminder> findDueBetweenPage(@Param("userId") Long userId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      Pageable pageable);

    @Query("""
            select r from Reminder r
            where r.userId = :userId and r.completedAt is not null
            order by r.completedAt desc
            """)
    Page<Reminder> findCompleted(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select r from Reminder r
            where r.userId = :userId
            order by r.dueAt asc, r.id asc
            """)
    Page<Reminder> findAllForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select r from Reminder r
            where r.userId = :userId and r.completedAt is null and r.dueAt between :from and :to
            order by r.dueAt asc
            """)
    List<Reminder> findDueBetween(@Param("userId") Long userId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);

    @Query("""
            select count(r) from Reminder r
            where r.userId = :userId and r.completedAt is null
            """)
    long countOpen(@Param("userId") Long userId);

    @Query("""
            select count(r) from Reminder r
            where r.userId = :userId and r.completedAt is null and r.dueAt < :now
            """)
    long countOverdue(@Param("userId") Long userId, @Param("now") Instant now);

    /** Platform-wide open reminder count, used by the admin dashboard. */
    @Query("select count(r) from Reminder r where r.completedAt is null")
    long countAllOpen();

    /** Reminders due in a window across all users - used by the e-mail reminder job. */
    @Query("""
            select r from Reminder r
            where r.completedAt is null
              and r.emailSentAt is null
              and r.dueAt between :from and :to
            order by r.dueAt asc
            """)
    List<Reminder> findDueForEmail(@Param("from") Instant from, @Param("to") Instant to);

    /** Auto-generated reminders that point at a given application (used when it is edited/deleted). */
    @Query("""
            select r from Reminder r
            where r.applicationId = :applicationId and r.source = com.jobtrack.domain.ReminderSource.AUTO
            """)
    List<Reminder> findAutoRemindersForApplication(@Param("applicationId") Long applicationId);

    @Query("""
            select r from Reminder r
            where r.applicationId = :applicationId
              and r.type = :type
              and r.source = com.jobtrack.domain.ReminderSource.AUTO
            """)
    List<Reminder> findAutoRemindersForApplicationAndType(@Param("applicationId") Long applicationId,
                                                          @Param("type") ReminderType type);

    /** Housekeeping: drop reminders whose application no longer exists (defensive, FK already cascades). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Reminder r where r.dueAt < :olderThan and r.completedAt is not null")
    int deleteCompletedOlderThan(@Param("olderThan") Instant olderThan);
}
