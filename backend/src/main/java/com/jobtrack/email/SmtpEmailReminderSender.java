package com.jobtrack.email;

import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SMTP implementation: one digest e-mail per user listing everything due soon.
 *
 * <p>Failures are logged and swallowed on purpose - an unreachable mail server must not fail the
 * reminder generation transaction or lose the reminders themselves. The caller only marks
 * {@code emailSentAt} when this method returns normally.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailReminderSender implements EmailReminderSender {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm z");

    private final JavaMailSender mailSender;
    private final String fromAddress;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void sendDigest(User user, List<Reminder> reminders) {
        if (reminders.isEmpty()) {
            return;
        }
        ZoneId zone = zoneOf(user);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("JobTrack: " + reminders.size() + " reminder(s) due soon");
            message.setText(buildBody(user, reminders, zone));
            mailSender.send(message);
            log.debug("Sent reminder digest to {}", user.getEmail());
        } catch (MailException e) {
            log.error("Could not send reminder digest to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String buildBody(User user, List<Reminder> reminders, ZoneId zone) {
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(user.getName()).append(",\n\n")
                .append("Here is what needs your attention in JobTrack (times in ")
                .append(zone.getId()).append("):\n\n");
        for (Reminder reminder : reminders) {
            body.append(" - ")
                    .append(ZonedDateTime.ofInstant(reminder.getDueAt(), zone).format(TIME_FORMAT))
                    .append("  ").append(reminder.getTitle());
            if (reminder.getDetails() != null) {
                body.append(" (").append(reminder.getDetails()).append(")");
            }
            body.append('\n');
        }
        body.append("\nOpen the dashboard to update or complete these items.\n");
        return body.toString();
    }

    private ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
