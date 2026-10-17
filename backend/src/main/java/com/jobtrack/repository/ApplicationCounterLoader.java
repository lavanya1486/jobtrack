package com.jobtrack.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch loaders that keep the list endpoints free of N+1 queries.
 *
 * <p>The list response carries {@code interviewCount} and {@code openReminderCount} per row. Loading
 * those one application at a time would mean 1 + 2N queries per page; these grouped queries keep the
 * cost constant.</p>
 */
@Component
public class ApplicationCounterLoader {

    @PersistenceContext
    private EntityManager entityManager;

    /** Interview rounds per application, restricted to the caller's own applications. */
    public Map<Long, Long> interviewCounts(Long userId, Collection<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        select i.application.id, count(i)
                        from Interview i
                        where i.application.userId = :userId
                          and i.application.id in :ids
                        group by i.application.id
                        """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("ids", applicationIds)
                .getResultList();
        return toMap(rows);
    }

    /** Open (not yet completed) reminders per application, restricted to the caller's own data. */
    public Map<Long, Long> openReminderCounts(Long userId, Collection<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        select r.applicationId, count(r)
                        from Reminder r
                        where r.userId = :userId
                          and r.completedAt is null
                          and r.applicationId in :ids
                        group by r.applicationId
                        """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("ids", applicationIds)
                .getResultList();
        return toMap(rows);
    }

    private Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return result;
    }
}
