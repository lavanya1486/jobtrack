package com.jobtrack.service;

import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.Reminder;
import com.jobtrack.dto.reminder.ReminderRequest;
import com.jobtrack.dto.reminder.ReminderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Maps reminders to DTOs and enriches them with the owning application's company / title.
 *
 * <p>The application reference is resolved from a batch-loaded map instead of a lazy association, so
 * listing 20 reminders costs one extra query rather than twenty.</p>
 */
@Component
@Slf4j
public class ReminderMapper {

    private final Clock clock;

    public ReminderMapper(Clock clock) {
        this.clock = clock;
    }

    public List<ReminderResponse> toResponses(List<Reminder> reminders,
                                              Map<Long, JobApplication> applicationsById,
                                              String timezone) {
        return reminders.stream()
                .map(reminder -> toResponse(reminder, applicationsById.get(reminder.getApplicationId()),
                        timezone))
                .toList();
    }

    public ReminderResponse toResponse(Reminder reminder, JobApplication application, String timezone) {
        ZoneId zone = resolve(timezone);
        Instant now = Instant.now(clock);

        return new ReminderResponse(
                reminder.getId(),
                reminder.getApplicationId(),
                application == null ? null : application.getCompany(),
                application == null ? null : application.getJobTitle(),
                reminder.getType(),
                reminder.getSource(),
                reminder.getTitle(),
                reminder.getDetails(),
                reminder.getDueAt(),
                ZonedDateTime.ofInstant(reminder.getDueAt(), zone),
                zone.getId(),
                reminder.getCompletedAt(),
                reminder.getNotifiedAt(),
                reminder.getEmailSentAt(),
                reminder.isCompleted(),
                reminder.isOverdue(now),
                Duration.between(now, reminder.getDueAt()).toHours(),
                reminder.getCreatedAt());
    }

    public void apply(Reminder reminder, ReminderRequest request) {
        reminder.setType(request.type());
        reminder.setTitle(request.title().trim());
        reminder.setDetails(StringUtils.hasText(request.details()) ? request.details().trim() : null);
        reminder.setDueAt(request.dueAt());
    }

    private ZoneId resolve(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return clock.getZone();
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException e) {
            return clock.getZone();
        }
    }
}
