package com.jobtrack.repository;

import com.jobtrack.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence for {@link User}. All look-ups are e-mail based and case-insensitive. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByEnabledTrue();

    @Query("select count(u) from User u where u.role = com.jobtrack.domain.Role.ADMIN")
    long countAdmins();

    /**
     * Admin user table: account fields plus activity counters.
     *
     * <p>Scalar subqueries instead of three left joins - joins across several to-many associations
     * would multiply rows and make the counters wrong (or need {@code count(distinct)} over a
     * cartesian product).</p>
     */
    @Query("""
            select u.id, u.name, u.email, u.role, u.enabled, u.timezone, u.createdAt,
                   (select count(a) from JobApplication a where a.userId = u.id),
                   (select count(i) from Interview i where i.application.userId = u.id),
                   (select count(r) from Reminder r where r.userId = u.id and r.completedAt is null)
            from User u
            order by u.createdAt desc
            """)
    List<Object[]> findAllWithActivityRaw();

    /** Type-safe wrapper around {@link #findAllWithActivityRaw()}. */
    default List<UserActivityRow> findAllWithActivity() {
        return findAllWithActivityRaw().stream()
                .map(row -> new UserActivityRow(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (com.jobtrack.domain.Role) row[3],
                        Boolean.TRUE.equals(row[4]),
                        (String) row[5],
                        (java.time.Instant) row[6],
                        ((Number) row[7]).longValue(),
                        ((Number) row[8]).longValue(),
                        ((Number) row[9]).longValue()))
                .toList();
    }

    /** Users that opted into e-mail reminders and have a reminder due in the given window. */
    @Query("""
            select distinct u
            from User u
            join Reminder r on r.userId = u.id
            where u.enabled = true
              and u.emailRemindersEnabled = true
              and r.completedAt is null
              and r.emailSentAt is null
              and r.dueAt between :from and :to
            """)
    List<User> findUsersWithRemindersDueBetween(@Param("from") Instant from, @Param("to") Instant to);
}
