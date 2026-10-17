package com.jobtrack.service;

import com.jobtrack.config.AppProperties;
import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.Interview;
import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.ReminderSource;
import com.jobtrack.domain.ReminderType;
import com.jobtrack.domain.User;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.ReminderRepository;
import com.jobtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates the reminders the app promises, and never creates the same one twice.
 *
 * <h2>Duplicate prevention</h2>
 * Every generated reminder carries a deterministic {@code dedupeKey}
 * ({@code deadline:<appId>:<date>}, {@code interview-prep:<interviewId>}, ...). Before inserting, the
 * service checks for an <em>open</em> reminder with that key, and the schema adds a partial unique
 * index on {@code (user_id, dedupe_key) where completed_at is null} as the final guard. Two scheduler
 * runs, two app instances or a manual re-run therefore cannot produce duplicates.
 *
 * <h2>Time zones</h2>
 * Date-only values (deadline, next action) become an instant at {@value #MORNING_HOUR}:00 in the
 * <em>owner's</em> time zone, and are stored as {@code timestamptz}. A deadline of 20 March for a user
 * in Kolkata fires at 03:30 UTC - the local morning, not midnight UTC.
 *
 * <h2>Staleness</h2>
 * When an application is edited or closed, open auto reminders whose key no longer applies are
 * deleted, so the dashboard never shows a reminder for a deadline that moved.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderGenerationService {

    /** Local hour at which a date-only reminder becomes due. */
    static final int MORNING_HOUR = 9;

    private final ReminderRepository reminderRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    /**
     * Syncs deadline / next-action reminders for a single application.
     * Called from create, update and status change so the dashboard is correct immediately.
     *
     * @return number of reminders created
     */
    @Transactional
    public int syncApplicationReminders(JobApplication application, Long userId, String timezone) {
        ZoneId zone = zoneOf(userId, timezone);
        Instant now = Instant.now(clock);

        Set<String> wantedKeys = new HashSet<>();
        List<Reminder> wanted = new ArrayList<>();

        if (application.getDeadline() != null && application.getStatus().isActive()) {
            String key = "deadline:" + application.getId() + ":" + application.getDeadline();
            wantedKeys.add(key);
            wanted.add(autoReminder(userId, application, ReminderType.APPLICATION_DEADLINE,
                    "Deadline: " + application.getCompany() + " - " + application.getJobTitle(),
                    morningOf(application.getDeadline(), zone), key));
        }

        if (application.getNextActionDate() != null && application.getStatus().isActive()) {
            String key = "next-action:" + application.getId() + ":" + application.getNextActionDate();
            wantedKeys.add(key);
            String action = application.getNextAction() == null ? "Next action" : application.getNextAction();
            wanted.add(autoReminder(userId, application, ReminderType.NEXT_ACTION,
                    action + " (" + application.getCompany() + ")",
                    morningOf(application.getNextActionDate(), zone), key));
        }

        // Drop stale open auto reminders (date changed, application closed or deleted).
        List<Reminder> stale = reminderRepository
                .findAutoRemindersForApplication(application.getId())
                .stream()
                .filter(existing -> !existing.isCompleted())
                .filter(existing -> existing.getDedupeKey() == null
                        || !wantedKeys.contains(existing.getDedupeKey()))
                .filter(existing -> existing.getType() == ReminderType.APPLICATION_DEADLINE
                        || existing.getType() == ReminderType.NEXT_ACTION)
                .toList();
        if (!stale.isEmpty()) {
            reminderRepository.deleteAll(stale);
            log.debug("Removed {} stale auto reminders for application {}", stale.size(), application.getId());
        }

        int created = 0;
        for (Reminder reminder : wanted) {
            if (reminder.getDueAt().isBefore(now.minus(Duration.ofDays(365)))) {
                continue; // ignore ancient dates, e.g. imported history
            }
            if (reminderRepository.existsByUserIdAndDedupeKeyAndCompletedAtIsNull(
                    reminder.getUserId(), reminder.getDedupeKey())) {
                continue;
            }
            try {
                reminderRepository.save(reminder);
                created++;
            } catch (org.springframework.dao.DataIntegrityViolationException race) {
                // Another node created it first - the partial unique index did its job.
                log.debug("Auto reminder {} already exists", reminder.getDedupeKey());
            }
        }
        return created;
    }

    /**
     * Keeps the prep reminder of one interview round in step with its schedule and outcome.
     *
     * <p>Moving a round moves the reminder; cancelling or completing the round removes it. Because the
     * dedupe key is stable ({@code interview-prep:<id>}) the reminder is updated in place rather than
     * duplicated.</p>
     */
    @Transactional
    public void syncInterviewReminder(Interview interview, Long userId, String timezone) {
        JobApplication application = interview.getApplication();
        String key = "interview-prep:" + interview.getId();
        Duration prepLead = appProperties.reminders().interviewPrepLeadTime();
        Instant now = Instant.now(clock);
        Instant dueAt = interview.getScheduledAt().minus(prepLead);

        boolean stillRelevant = interview.getOutcome() == InterviewOutcome.PENDING
                || interview.getOutcome() == InterviewOutcome.RESCHEDULED;
        String title = "Prepare: " + interview.getRoundType().name().replace('_', ' ').toLowerCase()
                + " interview at " + application.getCompany();

        Reminder existing = reminderRepository
                .findFirstByUserIdAndDedupeKeyAndCompletedAtIsNull(userId, key)
                .orElse(null);

        if (!stillRelevant || dueAt.isBefore(now)) {
            if (existing != null) {
                reminderRepository.delete(existing);
            }
            return;
        }

        if (existing != null) {
            existing.setDueAt(dueAt);
            existing.setTitle(title);
            existing.setDetails(application.getCompany() + " - " + application.getJobTitle());
            reminderRepository.save(existing);
            return;
        }

        Reminder reminder = new Reminder(userId, application.getId(), ReminderType.INTERVIEW_PREP,
                ReminderSource.AUTO, title, dueAt);
        reminder.setDedupeKey(key);
        reminder.setDetails(application.getCompany() + " - " + application.getJobTitle());
        reminderRepository.save(reminder);
    }

    /** Deletes an open auto reminder by dedupe key (used when its source object disappears). */
    @Transactional
    public void removeAutoReminder(String dedupeKey, Long userId) {
        reminderRepository.findFirstByUserIdAndDedupeKeyAndCompletedAtIsNull(userId, dedupeKey)
                .ifPresent(reminderRepository::delete);
    }

    /** Removes every auto reminder of an application (used on delete). */
    @Transactional
    public void removeAutoReminders(Long applicationId) {
        List<Reminder> reminders = reminderRepository.findAutoRemindersForApplication(applicationId);
        if (!reminders.isEmpty()) {
            reminderRepository.deleteAll(reminders);
        }
    }

    /**
     * Full scheduler pass: deadlines, next actions, interview prep and follow-ups for every user.
     *
     * @return number of reminders created
     */
    @Transactional
    public int generateForAllUsers() {
        int created = 0;
        Instant now = Instant.now(clock);
        int lookAheadDays = appProperties.reminders().lookAheadDays();
        Instant windowEnd = now.plus(Duration.ofDays(lookAheadDays));

        for (User user : userRepository.findAll()) {
            if (!user.isEnabled()) {
                continue;
            }
            try {
                created += generateForUser(user, now, windowEnd);
            } catch (RuntimeException e) {
                // One broken account (bad date, invalid zone, constraint race) must not stop the
                // run for everybody else; the next tick retries it.
                log.error("Reminder generation failed for user {}: {}", user.getId(), e.getMessage(), e);
            }
        }
        return created;
    }

    /** Visible for tests: generates everything for one user. */
    @Transactional
    public int generateForUser(User user, Instant now, Instant windowEnd) {
        int created = 0;
        ZoneId zone = zoneOf(user.getId(), user.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        // 1. Deadlines and next actions of open applications.
        List<JobApplication> applications = applicationRepository.findActiveWithDates(
                user.getId(), ApplicationStatus.ACTIVE_STATUSES);
        for (JobApplication application : applications) {
            created += syncApplicationReminders(application, user.getId(), user.getTimezone());
        }

        // 2. Follow-ups for submitted applications nobody answered yet.
        Duration followUpAfter = appProperties.reminders().followUpAfter();
        // Duration cannot be applied to a LocalDate (no time-of-day), so convert to whole days.
        LocalDate followUpCutoff = today.minusDays(followUpAfter.toDays());
        for (JobApplication application : applications) {
            if (application.getStatus() != ApplicationStatus.APPLIED
                    || application.getAppliedAt() == null
                    || application.getAppliedAt().isAfter(followUpCutoff)
                    || application.hasRecordedEmployerResponse()) {
                continue;
            }
            String key = "follow-up:" + application.getId();
            Instant dueAt = morningOf(
                    application.getAppliedAt().plusDays(followUpAfter.toDays()), zone);
            created += createIfMissing(autoReminder(user.getId(), application, ReminderType.FOLLOW_UP,
                    "Follow up: " + application.getCompany() + " - " + application.getJobTitle(),
                    dueAt, key));
        }

        // 3. Interview preparation reminders inside the look-ahead window.
        Duration prepLead = appProperties.reminders().interviewPrepLeadTime();
        List<Interview> interviews = interviewRepository.findPendingForUserBetween(
                user.getId(), now.minus(prepLead), windowEnd, InterviewOutcome.PENDING);
        for (Interview interview : interviews) {
            String key = "interview-prep:" + interview.getId();
            Instant dueAt = interview.getScheduledAt().minus(prepLead);
            JobApplication application = interview.getApplication();
            created += createIfMissing(autoReminder(user.getId(), application, ReminderType.INTERVIEW_PREP,
                    "Prepare: " + interview.getRoundType().name().replace('_', ' ').toLowerCase()
                            + " interview at " + application.getCompany(),
                    dueAt, key));
        }
        return created;
    }

    private int createIfMissing(Reminder reminder) {
        if (reminderRepository.existsByUserIdAndDedupeKeyAndCompletedAtIsNull(
                reminder.getUserId(), reminder.getDedupeKey())) {
            return 0;
        }
        try {
            reminderRepository.save(reminder);
            return 1;
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            log.debug("Auto reminder {} already exists", reminder.getDedupeKey());
            return 0;
        }
    }

    private Reminder autoReminder(Long userId, JobApplication application, ReminderType type,
                                  String title, Instant dueAt, String dedupeKey) {
        Reminder reminder = new Reminder(userId, application.getId(), type, ReminderSource.AUTO,
                title, dueAt);
        reminder.setDedupeKey(dedupeKey);
        reminder.setDetails(application.getCompany() + " - " + application.getJobTitle());
        return reminder;
    }

    /** 09:00 in the owner's zone, as an instant. */
    private Instant morningOf(LocalDate date, ZoneId zone) {
        return ZonedDateTime.of(date, LocalTime.of(MORNING_HOUR, 0), zone).toInstant();
    }

    /** Resolves the owner's zone, falling back to the application clock when it is unusable. */
    private ZoneId zoneOf(Long userId, String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return clock.getZone();
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException e) {
            log.warn("User {} has an invalid time zone '{}', falling back to {}",
                    userId, timezone, clock.getZone());
            return clock.getZone();
        }
    }
}
