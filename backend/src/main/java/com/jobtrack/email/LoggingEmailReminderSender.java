package com.jobtrack.email;

import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.User;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Default no-SMTP implementation: writes the digest to the log instead of sending it.
 *
 * <p>Keeps the reminder pipeline observable in development and CI, where no mail server exists, and
 * makes the "advanced feature" switchable rather than a hard dependency.</p>
 */
@Slf4j
public class LoggingEmailReminderSender implements EmailReminderSender {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void sendDigest(User user, List<Reminder> reminders) {
        log.info("[email disabled] Would send {} reminder(s) to {} <{}>: {}",
                reminders.size(), user.getName(), user.getEmail(),
                reminders.stream().map(Reminder::getTitle).toList());
    }
}
