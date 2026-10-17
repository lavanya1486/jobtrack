package com.jobtrack.service;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.ApplicationStatusHistory;
import com.jobtrack.domain.Interview;
import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.dto.common.PageResponse;
import com.jobtrack.dto.interview.InterviewRequest;
import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.NotFoundException;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import com.jobtrack.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Interview rounds: scheduling, rescheduling, outcomes and the automatic status bump they imply.
 *
 * <p>Business rule worth explaining in an interview: booking a round proves the employer responded and
 * that the process reached the interview stage, so the application status is advanced to
 * {@code INTERVIEW} automatically (through {@link ApplicationStatusPolicy} and the audit trail)
 * instead of asking the user to make two edits that could disagree.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    /** Statuses that may be advanced automatically when a round is booked. */
    private static final Set<ApplicationStatus> AUTO_ADVANCE_FROM =
            Set.of(ApplicationStatus.APPLIED, ApplicationStatus.ASSESSMENT);

    private static final Set<InterviewOutcome> OPEN_OUTCOMES = InterviewOutcome.openOutcomes();

    private final InterviewRepository interviewRepository;
    private final JobApplicationRepository applicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final InterviewMapper mapper;
    private final ReminderGenerationService reminderGenerationService;
    private final Clock clock;

    // ------------------------------------------------------------------ reads ----

    @Transactional(readOnly = true)
    public List<InterviewResponse> listForApplication(AuthenticatedUser currentUser, Long applicationId) {
        requireOwnedApplication(currentUser.id(), applicationId);
        return mapper.toResponses(
                interviewRepository.findByApplicationIdAndUserId(applicationId, currentUser.id()),
                currentUser.timezone());
    }

    @Transactional(readOnly = true)
    public PageResponse<InterviewResponse> list(AuthenticatedUser currentUser, InterviewScope scope,
                                                int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Instant now = Instant.now(clock);

        Page<Interview> result = switch (scope) {
            case UPCOMING -> interviewRepository
                    .findUpcomingPage(currentUser.id(), now, OPEN_OUTCOMES, pageable);
            case PAST -> interviewRepository
                    .findPastPage(currentUser.id(), now, OPEN_OUTCOMES, pageable);
            case ALL -> interviewRepository.findAllForUser(currentUser.id(), pageable);
        };
        return PageResponse.from(result,
                interview -> mapper.toResponse(interview, currentUser.timezone()));
    }

    /** Next rounds for the dashboard, soonest first. */
    @Transactional(readOnly = true)
    public List<InterviewResponse> upcoming(AuthenticatedUser currentUser, int limit) {
        List<Interview> interviews = interviewRepository.findUpcoming(currentUser.id(),
                Instant.now(clock), OPEN_OUTCOMES, PageRequest.of(0, limit));
        return mapper.toResponses(interviews, currentUser.timezone());
    }

    // ----------------------------------------------------------------- writes ----

    @Transactional
    public InterviewResponse create(AuthenticatedUser currentUser, Long applicationId,
                                    InterviewRequest request) {
        JobApplication application = requireOwnedApplication(currentUser.id(), applicationId);

        if (application.getStatus() == ApplicationStatus.SAVED) {
            throw new BadRequestException(
                    "Submit the application before scheduling interviews (current status is SAVED)");
        }
        if (application.getStatus().isClosed()
                && application.getStatus() != ApplicationStatus.REJECTED) {
            throw new BadRequestException("Cannot schedule an interview for a "
                    + application.getStatus().name().toLowerCase(Locale.ROOT) + " application");
        }

        Interview interview = new Interview();
        interview.setApplication(application);
        mapper.apply(interview, request, true);
        interviewRepository.save(interview);

        advanceToInterview(application, currentUser.id(), interview);
        reminderGenerationService.syncInterviewReminder(interview, currentUser.id(),
                currentUser.timezone());

        log.info("User {} scheduled {} interview for application {}", currentUser.id(),
                interview.getRoundType(), applicationId);
        return mapper.toResponse(interview, currentUser.timezone());
    }

    @Transactional
    public InterviewResponse update(AuthenticatedUser currentUser, Long interviewId,
                                    InterviewRequest request) {
        Interview interview = load(currentUser.id(), interviewId);
        Instant previousTime = interview.getScheduledAt();

        mapper.apply(interview, request, false);
        interviewRepository.save(interview);

        if (!previousTime.equals(interview.getScheduledAt()) || interview.getOutcome() != InterviewOutcome.PENDING) {
            reminderGenerationService.syncInterviewReminder(interview, currentUser.id(),
                    currentUser.timezone());
        }
        return mapper.toResponse(interview, currentUser.timezone());
    }

    @Transactional
    public void delete(AuthenticatedUser currentUser, Long interviewId) {
        Interview interview = load(currentUser.id(), interviewId);
        reminderGenerationService.removeAutoReminder("interview-prep:" + interview.getId(),
                currentUser.id());
        interviewRepository.delete(interview);
    }

    // ---------------------------------------------------------------- helpers ----

    private Interview load(Long userId, Long interviewId) {
        return interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> NotFoundException.interview(interviewId));
    }

    private JobApplication requireOwnedApplication(Long userId, Long applicationId) {
        return applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> NotFoundException.application(applicationId));
    }

    /** Records the implied status change, if the policy allows it. */
    private void advanceToInterview(JobApplication application, Long userId, Interview interview) {
        ApplicationStatus from = application.getStatus();
        if (!AUTO_ADVANCE_FROM.contains(from)) {
            return;
        }
        application.setStatus(ApplicationStatus.INTERVIEW);
        if (application.getFirstResponseAt() == null) {
            application.setFirstResponseAt(Instant.now(clock));
        }
        applicationRepository.save(application);
        statusHistoryRepository.save(ApplicationStatusHistory.of(
                application.getId(), from, ApplicationStatus.INTERVIEW,
                "Interview round scheduled: " + interview.getRoundType().name(), userId));
    }

    /** Scope of {@code GET /api/interviews}. */
    public enum InterviewScope {
        UPCOMING, PAST, ALL;

        public static InterviewScope from(String raw) {
            if (raw == null || raw.isBlank()) {
                return UPCOMING;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown interview scope '" + raw + "'",
                        java.util.Map.of("acceptedValues", List.of("UPCOMING", "PAST", "ALL")));
            }
        }
    }
}
