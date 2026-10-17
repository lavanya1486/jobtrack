package com.jobtrack.service;

import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.Reminder;
import com.jobtrack.domain.ReminderSource;
import com.jobtrack.dto.common.PageResponse;
import com.jobtrack.dto.reminder.ReminderRequest;
import com.jobtrack.dto.reminder.ReminderResponse;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.NotFoundException;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.ReminderRepository;
import com.jobtrack.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reminder queries and mutations for the authenticated user.
 *
 * <p>Completing a reminder is idempotent (completing twice keeps the first timestamp) so a double
 * click or a retry never produces a confusing audit trail.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private static final int DEFAULT_UPCOMING_DAYS = 7;

    private final ReminderRepository reminderRepository;
    private final JobApplicationRepository applicationRepository;
    private final ReminderMapper mapper;
    private final Clock clock;

    // ------------------------------------------------------------------ reads ----

    @Transactional(readOnly = true)
    public PageResponse<ReminderResponse> list(AuthenticatedUser currentUser, Scope scope,
                                               int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Instant now = Instant.now(clock);

        Page<Reminder> result = switch (scope) {
            case OPEN -> reminderRepository.findOpen(currentUser.id(), pageable);
            case OVERDUE -> reminderRepository.findOverduePage(currentUser.id(), now, pageable);
            case COMPLETED -> reminderRepository.findCompleted(currentUser.id(), pageable);
            case UPCOMING -> reminderRepository.findDueBetweenPage(currentUser.id(), now,
                    now.plus(Duration.ofDays(DEFAULT_UPCOMING_DAYS)), pageable);
            case ALL -> reminderRepository.findAllForUser(currentUser.id(), pageable);
        };

        Map<Long, JobApplication> applications = loadApplications(currentUser.id(), result.getContent());
        return PageResponse.from(result, reminder -> mapper.toResponse(reminder,
                applications.get(reminder.getApplicationId()), currentUser.timezone()));
    }

    /** Open reminders due within the given number of days, including overdue ones. */
    @Transactional(readOnly = true)
    public List<ReminderResponse> upcoming(AuthenticatedUser currentUser, int days, int limit) {
        Instant now = Instant.now(clock);
        List<Reminder> reminders = reminderRepository.findDueBetween(currentUser.id(),
                now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(Math.max(days, 1))),
                PageRequest.of(0, limit));
        Map<Long, JobApplication> applications = loadApplications(currentUser.id(), reminders);
        return mapper.toResponses(reminders, applications, currentUser.timezone());
    }

    // ----------------------------------------------------------------- writes ----

    @Transactional
    public ReminderResponse create(AuthenticatedUser currentUser, ReminderRequest request) {
        Long applicationId = request.applicationId();
        if (applicationId != null
                && !applicationRepository.existsByIdAndUserId(applicationId, currentUser.id())) {
            // 404 rather than 400/403: do not confirm that somebody else's application exists.
            throw NotFoundException.application(applicationId);
        }
        if (request.dueAt().isBefore(Instant.now(clock).minus(Duration.ofDays(3650)))) {
            throw new BadRequestException("Due date is unreasonably far in the past");
        }

        Reminder reminder = new Reminder(currentUser.id(), applicationId, request.type(),
                ReminderSource.MANUAL, request.title().trim(), request.dueAt());
        reminder.setDetails(request.details() == null ? null : request.details().trim());
        reminderRepository.save(reminder);

        log.debug("User {} created reminder {} due at {}", currentUser.id(), reminder.getId(),
                reminder.getDueAt());
        return toResponse(currentUser, reminder);
    }

    @Transactional
    public ReminderResponse update(AuthenticatedUser currentUser, Long id, ReminderRequest request) {
        Reminder reminder = load(currentUser.id(), id);
        if (request.applicationId() != null
                && !applicationRepository.existsByIdAndUserId(request.applicationId(), currentUser.id())) {
            throw NotFoundException.application(request.applicationId());
        }
        if (!java.util.Objects.equals(reminder.getApplicationId(), request.applicationId())) {
            reminder.setApplicationId(request.applicationId());
        }
        mapper.apply(reminder, request);
        reminderRepository.save(reminder);
        return toResponse(currentUser, reminder);
    }

    /** Marks a reminder done. Idempotent: the original completion time is preserved. */
    @Transactional
    public ReminderResponse complete(AuthenticatedUser currentUser, Long id) {
        Reminder reminder = load(currentUser.id(), id);
        if (reminder.getCompletedAt() == null) {
            reminder.setCompletedAt(Instant.now(clock));
            reminderRepository.save(reminder);
        }
        return toResponse(currentUser, reminder);
    }

    @Transactional
    public ReminderResponse reopen(AuthenticatedUser currentUser, Long id) {
        Reminder reminder = load(currentUser.id(), id);
        reminder.setCompletedAt(null);
        reminderRepository.save(reminder);
        return toResponse(currentUser, reminder);
    }

    @Transactional
    public void delete(AuthenticatedUser currentUser, Long id) {
        reminderRepository.delete(load(currentUser.id(), id));
    }

    // ---------------------------------------------------------------- helpers ----

    private Reminder load(Long userId, Long id) {
        return reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> NotFoundException.reminder(id));
    }

    private ReminderResponse toResponse(AuthenticatedUser currentUser, Reminder reminder) {
        JobApplication application = reminder.getApplicationId() == null ? null
                : applicationRepository.findByIdAndUserId(reminder.getApplicationId(), currentUser.id())
                        .orElse(null);
        return mapper.toResponse(reminder, application, currentUser.timezone());
    }

    /** Batch-loads the referenced applications of a reminder page (ownership scoped). */
    private Map<Long, JobApplication> loadApplications(Long userId, List<Reminder> reminders) {
        Set<Long> ids = reminders.stream()
                .map(Reminder::getApplicationId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, JobApplication> result = new HashMap<>();
        applicationRepository.findAllById(ids).forEach(application -> {
            if (application.getUserId().equals(userId)) {
                result.put(application.getId(), application);
            }
        });
        return result;
    }

    /** Filter for {@code GET /api/reminders}. */
    public enum Scope {
        OPEN, OVERDUE, UPCOMING, COMPLETED, ALL;

        public static Scope from(String raw) {
            if (raw == null || raw.isBlank()) {
                return OPEN;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown reminder scope '" + raw + "'",
                        Map.of("acceptedValues", List.of("OPEN", "OVERDUE", "UPCOMING",
                                "COMPLETED", "ALL")));
            }
        }
    }
}
