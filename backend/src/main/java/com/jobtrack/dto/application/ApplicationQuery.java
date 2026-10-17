package com.jobtrack.dto.application;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.EmploymentType;
import com.jobtrack.domain.WorkMode;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Server-side filter/sort/pagination criteria for {@code GET /api/applications}.
 *
 * <p>Filters are combined into one JPA {@code Specification} and executed as a single paged query -
 * the browser never receives the full table. Sort keys are whitelisted by
 * {@link SortField#from(String)} so a caller cannot inject an arbitrary property path.</p>
 *
 * @param userId owner scope, always taken from the authenticated principal (never from the request)
 */
public record ApplicationQuery(
        Long userId,
        String search,
        List<ApplicationStatus> statuses,
        List<EmploymentType> employmentTypes,
        List<WorkMode> workModes,
        String location,
        String tag,
        LocalDate appliedFrom,
        LocalDate appliedTo,
        LocalDate deadlineFrom,
        LocalDate deadlineTo,
        Boolean overdueOnly,
        Boolean submittedOnly,
        SortKey sort,
        int page,
        int size
) {

    /**
     * An inverted range would silently return zero rows, which reads like "no data" to the user
     * instead of "your filter is wrong" - so it is rejected here, once, for every caller.
     */
    public ApplicationQuery {
        if (appliedFrom != null && appliedTo != null && appliedFrom.isAfter(appliedTo)) {
            throw new com.jobtrack.exception.BadRequestException(
                    "appliedFrom must be on or before appliedTo",
                    java.util.Map.of("appliedFrom", appliedFrom.toString(), "appliedTo", appliedTo.toString()));
        }
        if (deadlineFrom != null && deadlineTo != null && deadlineFrom.isAfter(deadlineTo)) {
            throw new com.jobtrack.exception.BadRequestException(
                    "deadlineFrom must be on or before deadlineTo",
                    java.util.Map.of("deadlineFrom", deadlineFrom.toString(),
                            "deadlineTo", deadlineTo.toString()));
        }
    }

    /** Whitelisted sortable columns, mapped to entity attributes. */
    public enum SortField {
        NEWEST("createdAt", Direction.DESC),
        OLDEST("createdAt", Direction.ASC),
        APPLIED_DATE("appliedAt", Direction.DESC),
        DEADLINE("deadline", Direction.ASC),
        COMPANY("company", Direction.ASC),
        TITLE("jobTitle", Direction.ASC),
        STATUS("status", Direction.ASC),
        SALARY("expectedSalaryMax", Direction.DESC),
        UPDATED("updatedAt", Direction.DESC);

        private final String attribute;
        private final Direction direction;

        SortField(String attribute, Direction direction) {
            this.attribute = attribute;
            this.direction = direction;
        }

        public String attribute() {
            return attribute;
        }

        public Direction direction() {
            return direction;
        }

        /**
         * Resolves a user-supplied sort key such as {@code deadline} or {@code -company}.
         * A leading {@code -} flips the direction. Unknown keys raise {@link IllegalArgumentException}
         * which the global handler turns into {@code 400 Bad Request}.
         */
        public static SortKey from(String raw) {
            if (raw == null || raw.isBlank()) {
                return new SortKey(NEWEST, NEWEST.direction());
            }
            String normalised = raw.trim().toLowerCase(Locale.ROOT);
            boolean descending = normalised.startsWith("-");
            String key = descending ? normalised.substring(1) : normalised;
            // Underscores are ignored on both sides so "applied_date", "APPLIED_DATE" and
            // "applieddate" all resolve to the same whitelisted column.
            String squashedKey = key.replace("_", "");
            SortField field = Arrays.stream(values())
                    .filter(candidate -> candidate.name().equalsIgnoreCase(key)
                            || candidate.name().replace("_", "").equalsIgnoreCase(squashedKey))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown sort key '" + raw + "'. Valid keys: " + validKeys()));
            Direction direction = descending ? field.direction().opposite() : field.direction();
            return new SortKey(field, direction);
        }

        public static List<String> validKeys() {
            return Arrays.stream(values()).map(Enum::name).toList();
        }
    }

    public enum Direction {
        ASC, DESC;

        public Direction opposite() {
            return this == ASC ? DESC : ASC;
        }
    }

    /** A resolved sort key: whitelisted attribute plus effective direction. */
    public record SortKey(SortField field, Direction direction) {
    }
}
