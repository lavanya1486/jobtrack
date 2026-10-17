package com.jobtrack.service;

import com.jobtrack.domain.Interview;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.dto.interview.InterviewRequest;
import com.jobtrack.dto.interview.InterviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Maps interview rounds to DTOs, rendering {@code scheduledAt} in the owner's time zone.
 *
 * <p>Storing an instant and formatting per user is what keeps "10:30" correct for a candidate in
 * Kolkata interviewing with a company in Berlin.</p>
 */
@Component
@Slf4j
public class InterviewMapper {

    private final Clock clock;

    public InterviewMapper(Clock clock) {
        this.clock = clock;
    }

    public List<InterviewResponse> toResponses(List<Interview> interviews, String timezone) {
        return interviews.stream().map(interview -> toResponse(interview, timezone)).toList();
    }

    public InterviewResponse toResponse(Interview interview, String timezone) {
        JobApplication application = interview.getApplication();
        ZoneId zone = resolve(timezone);
        Instant now = Instant.now(clock);

        return new InterviewResponse(
                interview.getId(),
                application.getId(),
                application.getCompany(),
                application.getJobTitle(),
                interview.getRoundType(),
                interview.getScheduledAt(),
                ZonedDateTime.ofInstant(interview.getScheduledAt(), zone),
                zone.getId(),
                interview.getDurationMinutes(),
                interview.getLocationOrLink(),
                interview.getInterviewerName(),
                interview.getPrepNotes(),
                interview.getFeedback(),
                interview.getOutcome(),
                interview.isUpcoming(now),
                Duration.between(now, interview.getScheduledAt()).toMinutes(),
                interview.getCreatedAt(),
                interview.getUpdatedAt());
    }

    /** Copies a request onto an entity; {@code null} fields keep their current value. */
    public void apply(Interview interview, InterviewRequest request, boolean creating) {
        if (request.roundType() != null) {
            interview.setRoundType(request.roundType());
        }
        if (request.scheduledAt() != null) {
            interview.setScheduledAt(request.scheduledAt());
        }
        if (request.durationMinutes() != null) {
            interview.setDurationMinutes(request.durationMinutes());
        }
        if (request.outcome() != null) {
            interview.setOutcome(request.outcome());
        }
        if (creating || request.locationOrLink() != null) {
            interview.setLocationOrLink(blankToNull(request.locationOrLink()));
        }
        if (creating || request.interviewerName() != null) {
            interview.setInterviewerName(blankToNull(request.interviewerName()));
        }
        if (creating || request.prepNotes() != null) {
            interview.setPrepNotes(blankToNull(request.prepNotes()));
        }
        if (creating || request.feedback() != null) {
            interview.setFeedback(blankToNull(request.feedback()));
        }
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

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
