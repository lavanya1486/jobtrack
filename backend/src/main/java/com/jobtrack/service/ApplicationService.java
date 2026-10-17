package com.jobtrack.service;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.ApplicationStatusHistory;
import com.jobtrack.domain.EmploymentType;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.WorkMode;
import com.jobtrack.dto.application.ApplicationDetailResponse;
import com.jobtrack.dto.application.ApplicationQuery;
import com.jobtrack.dto.application.ApplicationRequest;
import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.application.FilterOptionsResponse;
import com.jobtrack.dto.application.StatusHistoryResponse;
import com.jobtrack.dto.application.StatusUpdateRequest;
import com.jobtrack.dto.common.PageResponse;
import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.exception.NotFoundException;
import com.jobtrack.repository.ApplicationCounterLoader;
import com.jobtrack.repository.ApplicationSpecifications;
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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Business rules for job applications: ownership, status transitions, audit trail and reminder sync.
 *
 * <p>Controllers stay thin - they only translate HTTP into these calls. Every read and write is
 * scoped by the authenticated user id, so a caller can never reach another user's row even by
 * guessing an id (see the IDOR tests in {@code ApplicationApiIT}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ApplicationCounterLoader counterLoader;
    private final ApplicationMapper mapper;
    private final InterviewMapper interviewMapper;
    private final ReminderGenerationService reminderGenerationService;
    private final Clock clock;

    // ------------------------------------------------------------------ reads ----

    /**
     * Search + filter + sort + paginate in one query.
     *
     * <p>Per-row counters (interviews, open reminders) are loaded with two grouped queries for the
     * whole page instead of one query per row.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> list(ApplicationQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size());
        ApplicationQuery.SortKey sortKey = query.sort() == null
                ? new ApplicationQuery.SortKey(ApplicationQuery.SortField.NEWEST,
                        ApplicationQuery.SortField.NEWEST.direction())
                : query.sort();

        Page<JobApplication> page = applicationRepository.findAll(
                ApplicationSpecifications.forQuery(query, sortKey), pageable);

        List<Long> ids = page.getContent().stream().map(JobApplication::getId).toList();
        Map<Long, Long> interviewCounts = counterLoader.interviewCounts(query.userId(), ids);
        Map<Long, Long> reminderCounts = counterLoader.openReminderCounts(query.userId(), ids);

        return PageResponse.from(page, application -> mapper.toResponse(
                application,
                interviewCounts.getOrDefault(application.getId(), 0L).intValue(),
                reminderCounts.getOrDefault(application.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(AuthenticatedUser currentUser, Long id) {
        JobApplication application = load(currentUser.id(), id);
        return mapper.toResponse(application,
                (int) interviewCount(currentUser.id(), id),
                (int) openReminderCount(currentUser.id(), id));
    }

    /** Application + interview rounds + status timeline, in one call for the detail page. */
    @Transactional(readOnly = true)
    public ApplicationDetailResponse getDetail(AuthenticatedUser currentUser, Long id) {
        JobApplication application = load(currentUser.id(), id);

        List<InterviewResponse> interviews = interviewMapper.toResponses(
                interviewRepository.findByApplicationIdAndUserId(id, currentUser.id()),
                currentUser.timezone());
        List<StatusHistoryResponse> history = statusHistoryRepository
                .findByApplicationIdOrderByChangedAtAscIdAsc(id).stream()
                .map(ApplicationMapper::toHistoryResponse)
                .toList();

        ApplicationResponse body = mapper.toResponse(application, interviews.size(),
                (int) openReminderCount(currentUser.id(), id));
        return new ApplicationDetailResponse(body, interviews, history);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(AuthenticatedUser currentUser, Long id) {
        load(currentUser.id(), id); // ownership check
        return statusHistoryRepository.findByApplicationIdOrderByChangedAtAscIdAsc(id).stream()
                .map(ApplicationMapper::toHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FilterOptionsResponse filterOptions(AuthenticatedUser currentUser) {
        return new FilterOptionsResponse(
                Arrays.asList(ApplicationStatus.values()),
                Arrays.asList(EmploymentType.values()),
                Arrays.asList(WorkMode.values()),
                applicationRepository.findDistinctTags(currentUser.id()),
                applicationRepository.findDistinctLocations(currentUser.id()),
                applicationRepository.findDistinctCompanies(currentUser.id()));
    }

    // ----------------------------------------------------------------- writes ----

    /**
     * Creates an application and writes the first audit entry.
     *
     * <p>A new record may start in any status - candidates adopt the tracker mid-process - but a
     * submitted status defaults {@code appliedAt} to today and stamps {@code firstResponseAt} when the
     * status already implies an employer response.</p>
     */
    @Transactional
    public ApplicationResponse create(AuthenticatedUser currentUser, ApplicationRequest request) {
        JobApplication application = new JobApplication(
                currentUser.id(), request.company().trim(), request.jobTitle().trim());

        mapper.apply(application, request, true);
        if (application.getAppliedAt() == null && application.getStatus().isSubmitted()) {
            application.setAppliedAt(LocalDate.now(clock));
        }

        applicationRepository.save(application);
        recordTransition(application, null, application.getStatus(), "Application created",
                currentUser.id());
        reminderGenerationService.syncApplicationReminders(application, currentUser.id(),
                currentUser.timezone());

        log.info("User {} created application {} ({} at {})", currentUser.id(), application.getId(),
                application.getJobTitle(), application.getCompany());
        return mapper.toResponse(application);
    }

    /** Full update. A status change inside the payload goes through the same policy as PATCH. */
    @Transactional
    public ApplicationResponse update(AuthenticatedUser currentUser, Long id,
                                      ApplicationRequest request) {
        JobApplication application = load(currentUser.id(), id);
        ApplicationStatus from = application.getStatus();
        ApplicationStatus to = request.status() == null ? from : request.status();

        if (from != to) {
            ApplicationStatusPolicy.validate(from, to);
        }

        mapper.apply(application, request, false);
        if (application.getAppliedAt() == null && application.getStatus().isSubmitted()) {
            application.setAppliedAt(LocalDate.now(clock));
        }
        applicationRepository.save(application);

        if (from != to) {
            recordTransition(application, from, to, "Status changed while editing the application",
                    currentUser.id());
        }
        reminderGenerationService.syncApplicationReminders(application, currentUser.id(),
                currentUser.timezone());
        return mapper.toResponse(application);
    }

    /**
     * Explicit status transition. Validates the move against {@link ApplicationStatusPolicy}, records
     * it in the audit trail and stamps the first employer response when relevant.
     */
    @Transactional
    public ApplicationResponse changeStatus(AuthenticatedUser currentUser, Long id,
                                            StatusUpdateRequest request) {
        JobApplication application = load(currentUser.id(), id);
        ApplicationStatus from = application.getStatus();
        ApplicationStatus to = request.status();

        ApplicationStatusPolicy.validate(from, to);
        application.setStatus(to);

        if (to.countsAsEmployerResponse() && application.getFirstResponseAt() == null) {
            application.setFirstResponseAt(Instant.now(clock));
        }
        if (to.isSubmitted() && application.getAppliedAt() == null) {
            application.setAppliedAt(LocalDate.now(clock));
        }
        if (to.isClosed()) {
            application.setNextActionDate(null);
            application.setNextAction(null);
        }

        applicationRepository.save(application);
        recordTransition(application, from, to, request.note(), currentUser.id());
        reminderGenerationService.syncApplicationReminders(application, currentUser.id(),
                currentUser.timezone());

        log.debug("User {} moved application {} from {} to {}", currentUser.id(), id, from, to);
        return mapper.toResponse(application);
    }

    /**
     * Deletes an application and everything hanging off it.
     *
     * <p>Interviews, reminders, tags and history rows disappear through database-level
     * {@code ON DELETE CASCADE}, so no orphan can survive even if a future code path forgets to clean
     * up.</p>
     */
    @Transactional
    public void delete(AuthenticatedUser currentUser, Long id) {
        JobApplication application = load(currentUser.id(), id);
        applicationRepository.delete(application);
        log.info("User {} deleted application {}", currentUser.id(), id);
    }

    // ---------------------------------------------------------------- helpers ----

    /** Ownership-scoped load; anything else is a 404 so ids cannot be probed. */
    private JobApplication load(Long userId, Long id) {
        return applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> NotFoundException.application(id));
    }

    private void recordTransition(JobApplication application, ApplicationStatus from,
                                  ApplicationStatus to, String note, Long userId) {
        statusHistoryRepository.save(ApplicationStatusHistory.of(
                application.getId(), from, to, note, userId));
    }

    private long interviewCount(Long userId, Long applicationId) {
        return counterLoader.interviewCounts(userId, List.of(applicationId))
                .getOrDefault(applicationId, 0L);
    }

    private long openReminderCount(Long userId, Long applicationId) {
        return counterLoader.openReminderCounts(userId, List.of(applicationId))
                .getOrDefault(applicationId, 0L);
    }

}
