package com.jobtrack.email;

import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.User;

import java.util.List;

/**
 * Sends reminder digests.
 *
 * <p>Abstracted behind an interface so the app runs (and tests pass) without SMTP credentials: the
 * default implementation only logs. Enabling {@code app.reminders.email-enabled=true} plus standard
 * {@code spring.mail.*} properties switches in the SMTP implementation.</p>
 */
public interface EmailReminderSender {

    /** True when e-mail delivery is actually configured. */
    boolean isEnabled();

    /** Sends one digest e-mail covering all given reminders. */
    void sendDigest(User user, List<Reminder> reminders);
}
