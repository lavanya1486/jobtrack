package com.jobtrack.config;

import com.jobtrack.email.EmailReminderSender;
import com.jobtrack.email.LoggingEmailReminderSender;
import com.jobtrack.email.SmtpEmailReminderSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the reminder e-mail implementation from configuration.
 *
 * <p>{@code app.reminders.email-enabled=true} + {@code spring.mail.host} selects the SMTP sender;
 * otherwise the logging sender keeps the feature observable without credentials.</p>
 */
@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.reminders", name = "email-enabled", havingValue = "true")
    public EmailReminderSender smtpEmailReminderSender(
            JavaMailSender mailSender,
            @Value("${app.reminders.email-from:no-reply@jobtrack.local}") String fromAddress) {
        return new SmtpEmailReminderSender(mailSender, fromAddress);
    }

    @Bean
    @ConditionalOnMissingBean(EmailReminderSender.class)
    public EmailReminderSender loggingEmailReminderSender() {
        return new LoggingEmailReminderSender();
    }
}
