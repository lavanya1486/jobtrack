package com.jobtrack.scheduler;

import com.jobtrack.config.AppProperties;
import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.User;
import com.jobtrack.email.EmailReminderSender;
import com.jobtrack.repository.ReminderRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.service.RefreshTokenService;
import com.jobtrack.service.ReminderGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The three background jobs.
 *
 * <p>The jobs are the only code that runs without a request, so they are tested directly: reminder
 * generation must be delegated, e-mail digests must respect both the global switch and the per-user
 * opt-in (and must mark what they sent, or every run would re-send it), and housekeeping must clean
 * up expired sessions.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderScheduler")
class ReminderSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-01-12T08:00:00Z");

    @Mock
    private ReminderGenerationService generationService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailReminderSender emailSender;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ReminderScheduler scheduler;

    private static AppProperties properties(boolean emailEnabled) {
        return new AppProperties(
                new AppProperties.Jwt("test-secret-value", Duration.ofMinutes(30),
                        Duration.ofDays(7), "jobtrack-api"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Reminders(true, "0 */15 * * * *", Duration.ofHours(48),
                        Duration.ofHours(24), Duration.ofDays(7), 14, emailEnabled,
                        Duration.ofHours(24), "0 0 8 * * *", "0 30 3 * * *",
                        "no-reply@jobtrack.local"),
                new AppProperties.Seed(false, "demo@jobtrack.local", "Demo1234!",
                        "admin@jobtrack.local", "Admin1234!"));
    }

    @BeforeEach
    void setUp() {
        scheduler = new ReminderScheduler(generationService, refreshTokenService, reminderRepository,
                userRepository, emailSender, properties(true), clock);
    }

    private Reminder reminder(long id, long userId, String title, Instant dueAt) {
        Reminder reminder = new Reminder();
        reminder.setId(id);
        reminder.setUserId(userId);
        reminder.setTitle(title);
        reminder.setDueAt(dueAt);
        return reminder;
    }

    private User user(long id, boolean enabled, boolean optedIn) {
        User user = new User();
        user.setId(id);
        user.setName("Candidate " + id);
        user.setEmail("candidate" + id + "@jobtrack.test");
        user.setTimezone("UTC");
        user.setEnabled(enabled);
        user.setEmailRemindersEnabled(optedIn);
        return user;
    }

    @Test
    @DisplayName("the generation job delegates to the idempotent generation service")
    void generatesReminders() {
        when(generationService.generateForAllUsers()).thenReturn(3);

        scheduler.generateReminders();

        verify(generationService).generateForAllUsers();
    }

    @Test
    @DisplayName("no e-mail is attempted when the feature switch is off")
    void skipsEmailWhenGloballyDisabled() {
        scheduler = new ReminderScheduler(generationService, refreshTokenService, reminderRepository,
                userRepository, emailSender, properties(false), clock);

        scheduler.sendEmailReminders();

        verifyNoInteractions(reminderRepository, userRepository, emailSender);
    }

    @Test
    @DisplayName("no e-mail is attempted when no SMTP server is configured")
    void skipsEmailWithoutSmtp() {
        when(emailSender.isEnabled()).thenReturn(false);

        scheduler.sendEmailReminders();

        verifyNoInteractions(reminderRepository, userRepository);
        verify(emailSender, never()).sendDigest(any(), any());
    }

    @Test
    @DisplayName("nothing is sent when no reminder is due in the lead-time window")
    void skipsEmailWhenNothingIsDue() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(reminderRepository.findDueForEmail(NOW, NOW.plus(Duration.ofHours(24))))
                .thenReturn(List.of());

        scheduler.sendEmailReminders();

        verify(emailSender, never()).sendDigest(any(), any());
        verify(reminderRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("one digest per opted-in user, and only sent reminders are stamped")
    void sendsOneDigestPerOptedInUser() {
        Reminder first = reminder(1L, 10L, "Deadline: Acme", NOW.plusSeconds(21600L));
        Reminder second = reminder(2L, 10L, "Prepare: technical round", NOW.plusSeconds(72000L));
        Reminder optedOut = reminder(3L, 20L, "Follow up with Acme", NOW.plusSeconds(32400L));
        Reminder deletedAccount = reminder(4L, 30L, "Deadline: Gone Co", NOW.plusSeconds(39600L));
        Reminder disabledAccount = reminder(5L, 40L, "Deadline: Paused Co", NOW.plusSeconds(43200L));
        List<Reminder> due = List.of(first, second, optedOut, deletedAccount, disabledAccount);

        when(emailSender.isEnabled()).thenReturn(true);
        when(reminderRepository.findDueForEmail(NOW, NOW.plus(Duration.ofHours(24)))).thenReturn(due);
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(10L, true, true),
                user(20L, true, false),
                user(40L, false, true)));

        scheduler.sendEmailReminders();

        // both reminders of user 10 go out in one digest, nobody else is e-mailed
        ArgumentCaptor<User> recipient = ArgumentCaptor.forClass(User.class);
        verify(emailSender, times(1)).sendDigest(recipient.capture(), eq(List.of(first, second)));
        assertThat(recipient.getValue().getId()).isEqualTo(10L);

        assertThat(first.getEmailSentAt()).isEqualTo(NOW);
        assertThat(second.getEmailSentAt()).isEqualTo(NOW);
        assertThat(optedOut.getEmailSentAt()).isNull();
        assertThat(deletedAccount.getEmailSentAt()).isNull();
        assertThat(disabledAccount.getEmailSentAt()).isNull();
        verify(reminderRepository).saveAll(due);
    }

    @Test
    @DisplayName("housekeeping drops expired refresh tokens and long-completed reminders")
    void runsHousekeeping() {
        when(refreshTokenService.purgeExpired()).thenReturn(4);
        lenient().when(reminderRepository.deleteCompletedOlderThan(any())).thenReturn(2);

        scheduler.housekeeping();

        verify(refreshTokenService).purgeExpired();
        verify(reminderRepository).deleteCompletedOlderThan(NOW.minus(Duration.ofDays(180)));
    }

    @Test
    @DisplayName("a user lookup that finds nothing is skipped instead of failing the run")
    void toleratesAMissingUser() {
        Reminder orphan = reminder(9L, 99L, "Deadline: Nobody", NOW.plusSeconds(10800L));
        when(emailSender.isEnabled()).thenReturn(true);
        when(reminderRepository.findDueForEmail(any(), any())).thenReturn(List.of(orphan));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        scheduler.sendEmailReminders();

        verify(emailSender, never()).sendDigest(any(), any());
        assertThat(orphan.getEmailSentAt()).isNull();
        verify(reminderRepository).saveAll(List.of(orphan));
    }
}
