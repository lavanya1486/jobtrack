package com.jobtrack.scheduler;

import com.jobtrack.config.AppProperties;
import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.User;
import com.jobtrack.email.EmailReminderSender;
import com.jobtrack.repository.ReminderRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.service.RefreshTokenService;
import com.jobtrack.service.ReminderGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Background jobs.
 *
 * <p>Three responsibilities, each with its own schedule so a slow e-mail run cannot delay reminder
 * generation:</p>
 * <ol>
 *   <li><b>Generation</b> ({@code app.reminders.cron}, default every 15 min) - create the reminders
 *       implied by deadlines, next actions, interview rounds and unanswered applications. Idempotent
 *       through dedupe keys, so overlapping runs or several instances are safe.</li>
 *   <li><b>E-mail digests</b> ({@code app.reminders.email-cron}, default daily) - only for users who
 *       opted in, and only once per reminder thanks to {@code email_sent_at}.</li>
 *   <li><b>Housekeeping</b> - drop expired refresh tokens and long-completed reminders.</li>
 * </ol>
 *
 * <p>Every job logs how much work it did, which is what makes the scheduler debuggable in
 * production instead of being a silent black box. The whole bean can be switched off with
 * {@code app.reminders.enabled=false} (used by the test profile).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.reminders", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final ReminderGenerationService reminderGenerationService;
    private final RefreshTokenService refreshTokenService;
    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final EmailReminderSender emailReminderSender;
    private final AppProperties appProperties;
    private final Clock clock;

    @Scheduled(cron = "${app.reminders.cron:0 */15 * * * *}")
    @Transactional
    public void generateReminders() {
        long started = System.nanoTime();
        int created = reminderGenerationService.generateForAllUsers();
        log.info("Reminder generation finished: {} reminder(s) created in {} ms", created,
                Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    @Scheduled(cron = "${app.reminders.email-cron:0 0 8 * * *}")
    @Transactional
    public void sendEmailReminders() {
        if (!appProperties.reminders().emailEnabled() || !emailReminderSender.isEnabled()) {
            log.debug("E-mail reminders disabled; skipping digest run");
            return;
        }

        Instant now = Instant.now(clock);
        Duration lead = appProperties.reminders().emailLeadTime();
        List<Reminder> due = reminderRepository.findDueForEmail(now, now.plus(lead));
        if (due.isEmpty()) {
            return;
        }

        Map<Long, List<Reminder>> byUser = due.stream()
                .collect(Collectors.groupingBy(Reminder::getUserId, LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)));
        Map<Long, User> users = userRepository.findAllById(byUser.keySet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        int sent = 0;
        for (Map.Entry<Long, List<Reminder>> entry : byUser.entrySet()) {
            User user = users.get(entry.getKey());
            if (user == null || !user.isEnabled() || !user.isEmailRemindersEnabled()) {
                continue;
            }
            emailReminderSender.sendDigest(user, entry.getValue());
            Instant sentAt = Instant.now(clock);
            entry.getValue().forEach(reminder -> reminder.setEmailSentAt(sentAt));
            sent += entry.getValue().size();
        }

        reminderRepository.saveAll(due);
        log.info("E-mail reminder run: {} reminder(s) across {} user(s) notified", sent, byUser.size());
    }

    @Scheduled(cron = "${app.reminders.housekeeping-cron:0 30 3 * * *}")
    @Transactional
    public void housekeeping() {
        int tokens = refreshTokenService.purgeExpired();
        int reminders = reminderRepository.deleteCompletedOlderThan(
                Instant.now(clock).minus(Duration.ofDays(180)));
        if (tokens > 0 || reminders > 0) {
            log.info("Housekeeping removed {} expired refresh token(s) and {} old completed reminder(s)",
                    tokens, reminders);
        }
    }
}
