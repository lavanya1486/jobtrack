package com.jobtrack.repository;

/**
 * Aggregate row grouped by calendar month.
 *
 * @param year  four-digit year
 * @param month month of year, 1-12
 * @param count rows in that bucket
 */
public record MonthlyCount(int year, int month, long count) {

    /** Factory tolerating whatever numeric type the JPA provider returns. */
    public static MonthlyCount of(Object year, Object month, Object count) {
        return new MonthlyCount(
                ((Number) year).intValue(),
                ((Number) month).intValue(),
                ((Number) count).longValue());
    }

    /** ISO year-month key, e.g. {@code 2026-03}. */
    public String key() {
        return "%04d-%02d".formatted(year, month);
    }
}
