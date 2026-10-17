package com.jobtrack.repository;

import com.jobtrack.domain.JobApplication;
import com.jobtrack.dto.application.ApplicationQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Composable predicates for {@code GET /api/applications}.
 *
 * <p>Why a {@link Specification} instead of a hand-written query with 12 nullable parameters:</p>
 * <ul>
 *   <li>filters combine safely (each becomes a parameterised predicate - no string concatenation,
 *       so no injection surface),</li>
 *   <li>the count query for pagination is derived from the same predicates,</li>
 *   <li>the ownership predicate is always applied first and cannot be dropped by a caller.</li>
 * </ul>
 *
 * <p>Ordering is applied here as well, using the whitelisted attribute from
 * {@link ApplicationQuery.SortField}, with {@code nulls last} for optional date columns.</p>
 */
public final class ApplicationSpecifications {

    private ApplicationSpecifications() {
    }

    /** Builds the full predicate set for a query, including the mandatory owner scope. */
    public static Specification<JobApplication> forQuery(ApplicationQuery query,
                                                         ApplicationQuery.SortKey sortKey) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Ownership - always applied, never derived from the request body.
            predicates.add(cb.equal(root.get("userId"), query.userId()));

            // 2. Free-text search over company + job title (case-insensitive, index friendly).
            if (StringUtils.hasText(query.search())) {
                String pattern = "%" + escapeLike(query.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("company")), pattern, '\\'),
                        cb.like(cb.lower(root.get("jobTitle")), pattern, '\\'),
                        cb.like(cb.lower(root.get("location")), pattern, '\\')));
            }

            // 3. Multi-value filters.
            if (query.statuses() != null && !query.statuses().isEmpty()) {
                predicates.add(root.get("status").in(query.statuses()));
            }
            if (query.employmentTypes() != null && !query.employmentTypes().isEmpty()) {
                predicates.add(root.get("employmentType").in(query.employmentTypes()));
            }
            if (query.workModes() != null && !query.workModes().isEmpty()) {
                predicates.add(root.get("workMode").in(query.workModes()));
            }

            // 4. Single-value filters.
            if (StringUtils.hasText(query.location())) {
                predicates.add(cb.equal(cb.lower(root.get("location")),
                        query.location().trim().toLowerCase()));
            }
            if (StringUtils.hasText(query.tag())) {
                predicates.add(cb.equal(cb.lower(root.join("tags")), query.tag().trim().toLowerCase()));
            }

            // 5. Date ranges.
            if (query.appliedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("appliedAt"), query.appliedFrom()));
            }
            if (query.appliedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("appliedAt"), query.appliedTo()));
            }
            if (query.deadlineFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("deadline"), query.deadlineFrom()));
            }
            if (query.deadlineTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("deadline"), query.deadlineTo()));
            }

            // 6. Boolean convenience filters used by the dashboard.
            if (Boolean.TRUE.equals(query.overdueOnly())) {
                predicates.add(cb.and(
                        cb.isNotNull(root.get("deadline")),
                        cb.lessThan(root.get("deadline"), java.time.LocalDate.now()),
                        root.get("status").in(
                                com.jobtrack.domain.ApplicationStatus.ACTIVE_STATUSES)));
            }
            if (Boolean.TRUE.equals(query.submittedOnly())) {
                predicates.add(cb.notEqual(root.get("status"),
                        com.jobtrack.domain.ApplicationStatus.SAVED));
            }

            // Skip ordering for the derived count query: an aggregate cannot be ordered by a
            // non-grouped column. The count query is recognised by its Long result type.
            if (criteriaQuery != null && !isCountQuery(criteriaQuery)) {
                criteriaQuery.orderBy(orderFor(root, cb, sortKey));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Order by the whitelisted attribute. Optional columns (deadline, applied date, salary) are
     * sorted with {@code null} values last so a "sort by deadline" view is not dominated by
     * applications that have no deadline.
     *
     * <p>Note on the tag filter: {@code application_tags} has a unique (application_id, tag) key, so
     * an equality join can match at most one row per application - the derived pagination count
     * query therefore stays correct without {@code distinct}.</p>
     */
    private static List<Order> orderFor(Root<JobApplication> root, CriteriaBuilder cb,
                                        ApplicationQuery.SortKey sortKey) {
        var path = root.get(sortKey.field().attribute());
        Order primary = sortKey.direction() == ApplicationQuery.Direction.DESC
                ? cb.desc(path)
                : cb.asc(path);

        boolean nullableColumn = switch (sortKey.field()) {
            case DEADLINE, APPLIED_DATE, SALARY -> true;
            default -> false;
        };
        if (nullableColumn) {
            Order nullsLastFlag = cb.asc(cb.selectCase()
                    .when(cb.isNull(path), 1)
                    .otherwise(0));
            return List.of(nullsLastFlag, primary);
        }
        return List.of(primary);
    }

    private static boolean isCountQuery(jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery) {
        Class<?> resultType = criteriaQuery.getResultType();
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }

    /** Escapes LIKE wildcards in user input so a search for {@code 100%} matches literally. */
    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
