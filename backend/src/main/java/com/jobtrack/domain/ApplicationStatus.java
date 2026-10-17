package com.jobtrack.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a job application.
 *
 * <p>The enum carries the single source of truth for how analytics classifies an application, so
 * the dashboard, the API docs and the tests can never disagree about a definition:</p>
 * <ul>
 *   <li>{@link #SAVED} is a draft: it is <em>excluded</em> from every rate denominator.</li>
 *   <li>A status in {@link #RESPONSE_STATUSES} means the employer reacted in some way
 *       (moved you forward, or rejected you).</li>
 *   <li>A status in {@link #INTERVIEW_STATUSES} means the process reached an interview stage.</li>
 * </ul>
 */
public enum ApplicationStatus {
    /** Saved for later, not submitted yet. */
    SAVED,
    /** Application submitted to the employer. */
    APPLIED,
    /** Online assessment / test invited or completed. */
    ASSESSMENT,
    /** At least one interview round scheduled or done. */
    INTERVIEW,
    /** Offer received. */
    OFFER,
    /** Employer declined. */
    REJECTED,
    /** Candidate withdrew. */
    WITHDRAWN;

    /** Statuses that prove the employer responded. */
    public static final Set<ApplicationStatus> RESPONSE_STATUSES =
            EnumSet.of(ASSESSMENT, INTERVIEW, OFFER, REJECTED);

    /** Statuses that prove the process reached the interview stage. */
    public static final Set<ApplicationStatus> INTERVIEW_STATUSES = EnumSet.of(INTERVIEW, OFFER);

    /** Statuses that are still in progress. */
    public static final Set<ApplicationStatus> ACTIVE_STATUSES = EnumSet.of(APPLIED, ASSESSMENT, INTERVIEW);

    /** Statuses that closed the process. */
    public static final Set<ApplicationStatus> CLOSED_STATUSES = EnumSet.of(OFFER, REJECTED, WITHDRAWN);

    /** A draft is never counted as a submitted application. */
    public boolean isDraft() {
        return this == SAVED;
    }

    public boolean isSubmitted() {
        return this != SAVED;
    }

    /** Moving into any of these statuses counts as "the employer responded". */
    public boolean countsAsEmployerResponse() {
        return RESPONSE_STATUSES.contains(this);
    }

    public boolean countsAsReachedInterview() {
        return INTERVIEW_STATUSES.contains(this);
    }

    public boolean isActive() {
        return ACTIVE_STATUSES.contains(this);
    }

    public boolean isClosed() {
        return CLOSED_STATUSES.contains(this);
    }
}
