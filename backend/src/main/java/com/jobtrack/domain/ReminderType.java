package com.jobtrack.domain;

/** What a reminder is about. */
public enum ReminderType {
    /** The job posting closes on this date. */
    APPLICATION_DEADLINE,
    /** Chase the recruiter after N days without an answer. */
    FOLLOW_UP,
    /** Prepare for a scheduled interview round. */
    INTERVIEW_PREP,
    /** The user-defined next action for an application. */
    NEXT_ACTION,
    /** Anything else the user creates manually. */
    CUSTOM
}
