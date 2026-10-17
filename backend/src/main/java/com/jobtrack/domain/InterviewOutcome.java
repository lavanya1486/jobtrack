package com.jobtrack.domain;

/** Result of an interview round. */
public enum InterviewOutcome {
    PENDING,
    PASSED,
    FAILED,
    RESCHEDULED,
    CANCELLED;

    /** True while the round has not been decided yet. */
    public boolean isOpen() {
        return this == PENDING || this == RESCHEDULED;
    }

    /**
     * Outcomes that still count as "upcoming". Every query and every DTO flag derives
     * from this set, so the tabs, the dashboard feed and the {@code upcoming} flag agree.
     */
    public static java.util.Set<InterviewOutcome> openOutcomes() {
        return java.util.Arrays.stream(values())
                .filter(InterviewOutcome::isOpen)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
