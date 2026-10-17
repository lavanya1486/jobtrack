package com.jobtrack.service;

import com.jobtrack.domain.JobApplication;
import com.jobtrack.dto.application.ApplicationRequest;
import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.application.ApplicationDetailResponse;
import com.jobtrack.dto.application.StatusHistoryResponse;
import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.ApplicationStatusHistory;
import com.jobtrack.dto.interview.InterviewResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps between the {@link JobApplication} entity and its API DTOs.
 *
 * <p>Entities are never returned to clients: that keeps the persistence model free to change, avoids
 * lazy-loading surprises during serialisation and guarantees {@code userId} is not leaked.</p>
 *
 * <p>Derived fields (deadline overdue, days until deadline, submitted) are calculated here once so
 * the React client and the API docs always agree on their meaning.</p>
 */
@Component
public class ApplicationMapper {

    private final Clock clock;

    public ApplicationMapper(Clock clock) {
        this.clock = clock;
    }

    public ApplicationResponse toResponse(JobApplication application) {
        return toResponse(application, 0, 0);
    }

    public ApplicationResponse toResponse(JobApplication application, int interviewCount,
                                          int openReminderCount) {
        LocalDate today = LocalDate.now(clock);

        Long daysUntilDeadline = application.getDeadline() == null ? null
                : ChronoUnit.DAYS.between(today, application.getDeadline());
        Long daysSinceApplied = application.getAppliedAt() == null ? null
                : ChronoUnit.DAYS.between(application.getAppliedAt(), today);

        return new ApplicationResponse(
                application.getId(),
                application.getCompany(),
                application.getJobTitle(),
                application.getJobDescription(),
                application.getJobUrl(),
                application.getSource(),
                application.getEmploymentType(),
                application.getWorkMode(),
                application.getLocation(),
                application.getCurrency(),
                application.getExpectedSalaryMin(),
                application.getExpectedSalaryMax(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getDeadline(),
                application.getNextActionDate(),
                application.getNextAction(),
                application.getNotes(),
                List.copyOf(orderedTags(application)),
                application.getFirstResponseAt(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                application.isDeadlineOverdue(today),
                application.getNextActionDate() != null
                        && !application.getNextActionDate().isAfter(today)
                        && !application.getStatus().isClosed(),
                daysUntilDeadline,
                daysSinceApplied,
                application.getStatus().isSubmitted(),
                interviewCount,
                openReminderCount);
    }

    public ApplicationDetailResponse toDetail(JobApplication application,
                                              List<InterviewResponse> interviews,
                                              List<ApplicationStatusHistory> history) {
        return new ApplicationDetailResponse(
                toResponse(application, interviews.size(), 0),
                interviews,
                history.stream().map(ApplicationMapper::toHistoryResponse).toList());
    }

    public static StatusHistoryResponse toHistoryResponse(ApplicationStatusHistory entry) {
        return new StatusHistoryResponse(entry.getId(), entry.getFromStatus(), entry.getToStatus(),
                entry.getNote(), entry.getChangedAt());
    }

    private Set<String> orderedTags(JobApplication application) {
        Set<String> tags = application.getTags();
        return tags == null ? Set.of() : new LinkedHashSet<>(tags);
    }

    /**
     * Copies a request onto an entity.
     *
     * @param creating when {@code true} the status may be any value (adopting the tool mid-process);
     *                 when {@code false} the caller must have validated the transition already.
     */
    public void apply(JobApplication application, ApplicationRequest request, boolean creating) {
        application.setCompany(trim(request.company()));
        application.setJobTitle(trim(request.jobTitle()));
        application.setJobDescription(blankToNull(request.jobDescription()));
        application.setJobUrl(blankToNull(request.jobUrl()));
        application.setSource(blankToNull(request.source()));
        application.setLocation(blankToNull(request.location()));
        application.setNextAction(blankToNull(request.nextAction()));
        application.setNotes(blankToNull(request.notes()));

        if (request.employmentType() != null) {
            application.setEmploymentType(request.employmentType());
        }
        if (request.workMode() != null) {
            application.setWorkMode(request.workMode());
        }
        if (StringUtils.hasText(request.currency())) {
            application.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        }

        application.setExpectedSalaryMin(request.expectedSalaryMin());
        application.setExpectedSalaryMax(request.expectedSalaryMax());
        application.setAppliedAt(request.appliedAt());
        application.setDeadline(request.deadline());
        application.setNextActionDate(request.nextActionDate());

        ApplicationStatus target = request.status() == null
                ? (creating ? ApplicationStatus.SAVED : application.getStatus())
                : request.status();
        application.setStatus(target);

        // An application created or moved straight into a response-worthy status has been answered
        // by the employer; stamping the moment keeps the response-rate numerator honest.
        if (target.countsAsEmployerResponse() && application.getFirstResponseAt() == null) {
            application.setFirstResponseAt(java.time.Instant.now(clock));
        }

        application.getTags().clear();
        if (request.tags() != null) {
            request.tags().stream()
                    .filter(StringUtils::hasText)
                    .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .limit(15)
                    .forEach(application.getTags()::add);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
