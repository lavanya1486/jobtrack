package com.jobtrack.email;

import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The SMTP digest sender.
 *
 * <p>E-mail is an optional feature, so the two things that matter are that a digest is rendered in
 * the recipient's own time zone and that an unreachable mail server cannot break the reminder job
 * that called it.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpEmailReminderSender")
class SmtpEmailReminderSenderTest {

    private static final String FROM = "no-reply@jobtrack.test";

    @Mock
    private JavaMailSender mailSender;

    private User user(String timezone) {
        User user = new User();
        user.setId(1L);
        user.setName("Priya Nair");
        user.setEmail("priya@jobtrack.test");
        user.setTimezone(timezone);
        return user;
    }

    private Reminder reminder(String title, String details, Instant dueAt) {
        Reminder reminder = new Reminder();
        reminder.setUserId(1L);
        reminder.setTitle(title);
        reminder.setDetails(details);
        reminder.setDueAt(dueAt);
        return reminder;
    }

    @Test
    @DisplayName("renders one digest per user with times in their own zone")
    void rendersTheDigestInTheUsersTimeZone() {
        SmtpEmailReminderSender sender = new SmtpEmailReminderSender(mailSender, FROM);
        // 09:00 UTC is 14:30 in Asia/Kolkata
        List<Reminder> reminders = List.of(
                reminder("Deadline: Acme - Backend Engineer", "Apply before the posting closes",
                        Instant.parse("2026-01-12T09:00:00Z")),
                reminder("Prepare: technical round", null, Instant.parse("2026-01-13T04:30:00Z")));

        sender.sendDigest(user("Asia/Kolkata"), reminders);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        SimpleMailMessage sent = message.getValue();

        assertThat(sent.getFrom()).isEqualTo(FROM);
        assertThat(sent.getTo()).containsExactly("priya@jobtrack.test");
        assertThat(sent.getSubject()).isEqualTo("JobTrack: 2 reminder(s) due soon");

        String body = sent.getText();
        assertThat(body).isNotNull();
        assertThat(body).contains("Hi Priya Nair")
                .contains("Asia/Kolkata")
                .contains("14:30")                                  // 09:00Z rendered locally
                .contains("Deadline: Acme - Backend Engineer")
                .contains("(Apply before the posting closes)")
                .contains("Prepare: technical round")
                .doesNotContain("(null)");
    }

    @Test
    @DisplayName("an unknown time zone falls back to UTC instead of failing the digest")
    void fallsBackToUtcForAnUnknownTimeZone() {
        SmtpEmailReminderSender sender = new SmtpEmailReminderSender(mailSender, FROM);

        sender.sendDigest(user("Mars/Olympus"),
                List.of(reminder("Deadline: Acme", null, Instant.parse("2026-01-12T09:00:00Z"))));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getText()).contains("times in UTC").contains("09:00");
    }

    @Test
    @DisplayName("an unreachable mail server is logged, not rethrown")
    void swallowsMailFailures() {
        SmtpEmailReminderSender sender = new SmtpEmailReminderSender(mailSender, FROM);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> sender.sendDigest(user("UTC"),
                List.of(reminder("Deadline: Acme", null, Instant.parse("2026-01-12T09:00:00Z")))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an empty digest is not sent at all")
    void doesNothingWithoutReminders() {
        SmtpEmailReminderSender sender = new SmtpEmailReminderSender(mailSender, FROM);

        sender.sendDigest(user("UTC"), List.of());

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("the SMTP sender reports itself enabled, the logging fallback does not")
    void reportsAvailability() {
        assertThat(new SmtpEmailReminderSender(mailSender, FROM).isEnabled()).isTrue();
        assertThat(new LoggingEmailReminderSender().isEnabled()).isFalse();
        // the fallback only logs, so it must never touch a mail server
        new LoggingEmailReminderSender().sendDigest(user("UTC"),
                List.of(reminder("Deadline: Acme", null, Instant.parse("2026-01-12T09:00:00Z"))));
        verifyNoInteractions(mailSender);
    }
}
