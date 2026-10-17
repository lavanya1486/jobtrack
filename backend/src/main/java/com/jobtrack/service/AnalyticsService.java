package com.jobtrack.service;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.Reminder;
import com.jobtrack.dto.analytics.AnalyticsSummaryResponse;
import com.jobtrack.dto.analytics.AnalyticsTrendsResponse;
import com.jobtrack.dto.analytics.FunnelStageResponse;
import com.jobtrack.dto.analytics.StatusSliceResponse;
import com.jobtrack.dto.analytics.TopCompanyResponse;
import com.jobtrack.dto.analytics.TrendPointResponse;
import com.jobtrack.dto.analytics.UpcomingEventResponse;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.MonthlyCount;
import com.jobtrack.repository.ReminderRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import com.jobtrack.repository.ApplicationStats;
import com.jobtrack.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every number on the dashboard, computed from the database.
 *
 * <h2>Definitions (kept in one place on purpose)</h2>
 * <ul>
 *   <li><b>Denominator</b> = {@code submittedApplications} (status != SAVED). Drafts are excluded
 *       because an application nobody received cannot be "answered".</li>
 *   <li><b>responseRate</b> = applications with {@code firstResponseAt} set / submitted.</li>
 *   <li><b>interviewRate</b> = applications that reached the interview stage (status INTERVIEW/OFFER
 *       <em>or</em> at least one recorded round) / submitted.</li>
 *   <li><b>offerRate</b> = applications currently at OFFER / submitted.</li>
 *   <li><b>Funnel</b> counts applications that <em>reached</em> each stage, read from the status
 *       history so a later rejection does not erase an earlier interview.</li>
 * </ul>
 *
 * <p>Nothing is hardcoded and no entity list is loaded into memory: every metric comes from an
 * aggregate query, so the dashboard stays flat as the number of applications grows.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private static final Set<InterviewOutcome> OPEN_OUTCOMES = InterviewOutcome.openOutcomes();
    private static final int MAX_TREND_MONTHS = 36;
    private static final int TOP_COMPANIES = 5;

    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final ReminderRepository reminderRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final Clock clock;

    // ---------------------------------------------------------------- summary ----

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(AuthenticatedUser currentUser) {
        Long userId = currentUser.id();
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);

        ApplicationStats stats = applicationRepository.aggregateStats(
                userId,
                ApplicationStatus.SAVED,
                ApplicationStatus.ACTIVE_STATUSES,
                ApplicationStatus.CLOSED_STATUSES,
                ApplicationStatus.OFFER,
                ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN,
                today);

        long reachedInterview = applicationRepository.countReachedInterview(userId,
                ApplicationStatus.INTERVIEW_STATUSES);
        long interviewsScheduled = interviewRepository.countForUser(userId);
        long upcomingInterviews = interviewRepository.countUpcoming(userId, now, OPEN_OUTCOMES);
        long passedInterviews = interviewRepository.countByOutcome(userId, InterviewOutcome.PASSED);
        long failedInterviews = interviewRepository.countByOutcome(userId, InterviewOutcome.FAILED);
        long completedInterviews = interviewsScheduled - upcomingInterviews;

        long openReminders = reminderRepository.countOpen(userId);
        long overdueReminders = reminderRepository.countOverdue(userId, now);

        List<StatusSliceResponse> distribution = statusDistribution(userId, stats.total());
        List<FunnelStageResponse> funnel = funnel(userId, stats.submitted());
        List<TopCompanyResponse> topCompanies = topCompanies(userId);

        Double averageResponseDays = round(applicationRepository.averageDaysToFirstResponse(userId), 1);

        return new AnalyticsSummaryResponse(
                stats.total(),
                stats.submitted(),
                stats.saved(),
                stats.active(),
                stats.closed(),
                stats.responded(),
                reachedInterview,
                stats.offers(),
                stats.rejections(),
                stats.withdrawals(),
                interviewsScheduled,
                upcomingInterviews,
                completedInterviews,
                passedInterviews,
                failedInterviews,
                stats.overdueDeadline(),
                stats.dueNextAction(),
                openReminders,
                overdueReminders,
                averageResponseDays,
                rate(stats.responded(), stats.submitted()),
                rate(reachedInterview, stats.submitted()),
                rate(stats.offers(), stats.submitted()),
                round(stats.averageSalaryMax(), 2),
                distribution,
                funnel,
                topCompanies);
    }

    // ----------------------------------------------------------------- trends ----

    /**
     * Monthly activity for the last {@code months} calendar months (current month included).
     * Months without activity are emitted as zeros so the chart axis stays continuous.
     */
    @Transactional(readOnly = true)
    public AnalyticsTrendsResponse trends(AuthenticatedUser currentUser, int months) {
        Long userId = currentUser.id();
        int window = Math.clamp(months, 1, MAX_TREND_MONTHS);

        YearMonth current = YearMonth.now(clock);
        YearMonth first = current.minusMonths(window - 1L);
        LocalDate fromDate = first.atDay(1);
        LocalDate toDate = current.atEndOfMonth();
        Instant fromInstant = fromDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant();

        Map<String, Long> applications = monthly(
                applicationRepository.monthlyApplications(userId, fromDate, toDate));
        Map<String, Long> responses = monthly(
                applicationRepository.monthlyResponses(userId, fromInstant, toInstant));
        Map<String, Long> interviews = monthly(
                interviewRepository.monthlyInterviews(userId, fromInstant, toInstant));
        Map<String, Long> offers = monthly(
                statusHistoryRepository.monthlyStatusCounts(userId, ApplicationStatus.OFFER,
                        fromInstant, toInstant));
        Map<String, Long> rejections = monthly(
                statusHistoryRepository.monthlyStatusCounts(userId, ApplicationStatus.REJECTED,
                        fromInstant, toInstant));

        List<TrendPointResponse> points = new ArrayList<>(window);
        long totalsApplications = 0;
        long totalsInterviews = 0;
        long totalsResponses = 0;
        long totalsOffers = 0;
        long totalsRejections = 0;
        String bestMonth = null;
        long bestMonthCount = -1;

        for (int i = 0; i < window; i++) {
            YearMonth month = first.plusMonths(i);
            String key = month.toString();

            long applicationCount = applications.getOrDefault(key, 0L);
            long interviewCount = interviews.getOrDefault(key, 0L);
            long responseCount = responses.getOrDefault(key, 0L);
            long offerCount = offers.getOrDefault(key, 0L);
            long rejectionCount = rejections.getOrDefault(key, 0L);

            points.add(new TrendPointResponse(
                    key,
                    month.getYear(),
                    month.getMonthValue(),
                    monthLabel(month),
                    applicationCount,
                    interviewCount,
                    responseCount,
                    offerCount,
                    rejectionCount));

            totalsApplications += applicationCount;
            totalsInterviews += interviewCount;
            totalsResponses += responseCount;
            totalsOffers += offerCount;
            totalsRejections += rejectionCount;

            if (applicationCount > bestMonthCount) {
                bestMonthCount = applicationCount;
                bestMonth = key;
            }
        }

        return new AnalyticsTrendsResponse(
                points,
                window,
                fromInstant,
                new AnalyticsTrendsResponse.TrendTotals(totalsApplications, totalsInterviews,
                        totalsResponses, totalsOffers, totalsRejections,
                        bestMonthCount > 0 ? bestMonth : null));
    }

    // --------------------------------------------------------------- dashboard ----

    /**
     * "Needs attention" list: upcoming interviews, approaching deadlines, due next actions and open
     * reminders, merged and sorted by urgency (overdue first).
     */
    @Transactional(readOnly = true)
    public List<UpcomingEventResponse> upcomingEvents(AuthenticatedUser currentUser, int days, int limit) {
        Long userId = currentUser.id();
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = zoneOf(currentUser.timezone());
        List<UpcomingEventResponse> events = new ArrayList<>();
        PageRequest page = PageRequest.of(0, Math.max(limit, 1));

        interviewRepository.findUpcoming(userId, Instant.now(clock), OPEN_OUTCOMES, page)
                .forEach(interview -> events.add(new UpcomingEventResponse(
                        "INTERVIEW",
                        interview.getId(),
                        interview.getApplication().getId(),
                        interview.getApplication().getCompany(),
                        interview.getApplication().getJobTitle(),
                        interview.getRoundType().name().replace('_', ' ').toLowerCase(Locale.ROOT)
                                + " interview",
                        null,
                        interview.getScheduledAt(),
                        formatTime(interview.getScheduledAt(), zone),
                        false,
                        daysBetween(Instant.now(clock), interview.getScheduledAt(), zone))));

        applicationRepository.findWithDeadlineUpTo(userId, today.plusDays(Math.max(days, 1)),
                        ApplicationStatus.ACTIVE_STATUSES, page).forEach(application ->
                events.add(deadlineEvent(application, today, zone)));

        applicationRepository.findWithNextActionDue(userId, today, ApplicationStatus.ACTIVE_STATUSES, page)
                .forEach(application -> events.add(nextActionEvent(application, today, zone)));

        reminderRepository.findOverdue(userId, Instant.now(clock), page).forEach(reminder ->
                events.add(reminderEvent(reminder, zone)));

        Comparator<UpcomingEventResponse> byUrgency = Comparator
                .comparing((UpcomingEventResponse event) -> !event.overdue())
                .thenComparing(event -> effectiveInstant(event, zone))
                .thenComparing(UpcomingEventResponse::company,
                        Comparator.nullsLast(Comparator.naturalOrder()));
        events.sort(byUrgency);

        return events.size() <= limit ? events : events.subList(0, limit);
    }

    // ---------------------------------------------------------------- helpers ----

    private UpcomingEventResponse deadlineEvent(JobApplication application, LocalDate today, ZoneId zone) {
        LocalDate deadline = application.getDeadline();
        return new UpcomingEventResponse(
                "DEADLINE",
                null,
                application.getId(),
                application.getCompany(),
                application.getJobTitle(),
                "Application deadline",
                deadline,
                null,
                formatDate(deadline, zone),
                deadline.isBefore(today),
                (long) deadline.toEpochDay() - today.toEpochDay());
    }

    private UpcomingEventResponse nextActionEvent(JobApplication application, LocalDate today, ZoneId zone) {
        LocalDate actionDate = application.getNextActionDate();
        String title = application.getNextAction() == null ? "Next action" : application.getNextAction();
        return new UpcomingEventResponse(
                "NEXT_ACTION",
                null,
                application.getId(),
                application.getCompany(),
                application.getJobTitle(),
                title,
                actionDate,
                null,
                formatDate(actionDate, zone),
                actionDate.isBefore(today),
                (long) actionDate.toEpochDay() - today.toEpochDay());
    }

    private UpcomingEventResponse reminderEvent(Reminder reminder, ZoneId zone) {
        return new UpcomingEventResponse(
                "REMINDER",
                reminder.getId(),
                reminder.getApplicationId(),
                null,
                null,
                reminder.getTitle(),
                null,
                reminder.getDueAt(),
                formatTime(reminder.getDueAt(), zone),
                true,
                daysBetween(Instant.now(clock), reminder.getDueAt(), zone));
    }

    private List<StatusSliceResponse> statusDistribution(Long userId, long total) {
        List<Object[]> rows = applicationRepository.countGroupedByStatus(userId);
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (Object[] row : rows) {
            counts.put((ApplicationStatus) row[0], ((Number) row[1]).longValue());
        }
        // Every status is emitted, including zero counts, so the chart legend is stable.
        List<StatusSliceResponse> slices = new ArrayList<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            slices.add(new StatusSliceResponse(status, count, rate(count, total)));
        }
        return slices;
    }

    private List<FunnelStageResponse> funnel(Long userId, long submitted) {
        long[] counts = statusHistoryRepository.funnelCounts(userId);
        long applied = Math.max(counts[0], submitted); // never below the submitted total
        String[] stages = {"APPLIED", "ASSESSMENT", "INTERVIEW", "OFFER"};
        String[] labels = {"Applied", "Assessment", "Interview", "Offer"};
        long[] values = {applied, counts[1], counts[2], counts[3]};

        List<FunnelStageResponse> funnel = new ArrayList<>(stages.length);
        for (int i = 0; i < stages.length; i++) {
            long previous = i == 0 ? applied : values[i - 1];
            funnel.add(new FunnelStageResponse(
                    stages[i],
                    labels[i],
                    values[i],
                    rate(values[i], previous),
                    rate(values[i], applied)));
        }
        return funnel;
    }

    private List<TopCompanyResponse> topCompanies(Long userId) {
        List<Object[]> rows = applicationRepository.topCompanies(userId,
                ApplicationStatus.INTERVIEW_STATUSES, ApplicationStatus.OFFER,
                PageRequest.of(0, TOP_COMPANIES));
        List<TopCompanyResponse> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(new TopCompanyResponse(
                    (String) row[0],
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue()));
        }
        return result;
    }

    private Map<String, Long> monthly(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            MonthlyCount count = MonthlyCount.of(row[0], row[1], row[2]);
            result.put(count.key(), count.count());
        }
        return result;
    }

    private String monthLabel(YearMonth month) {
        return Month.of(month.getMonthValue()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + month.getYear();
    }

    /** Rates are 0 when the denominator is 0, never NaN, and rounded to 4 decimals (0.0001 = 0.01%). */
    static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return round((double) numerator / denominator, 4);
    }

    private static Double round(Double value, int decimals) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private Instant effectiveInstant(UpcomingEventResponse event, ZoneId zone) {
        if (event.at() != null) {
            return event.at();
        }
        return event.date() == null ? Instant.MAX
                : event.date().atStartOfDay(zone).toInstant();
    }

    private Long daysBetween(Instant from, Instant to, ZoneId zone) {
        return Duration.between(from, to).toDays();
    }

    private String formatDate(LocalDate date, ZoneId zone) {
        return date == null ? null : date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private String formatTime(Instant instant, ZoneId zone) {
        return instant == null ? null
                : java.time.ZonedDateTime.ofInstant(instant, zone)
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm"));
    }

    private ZoneId zoneOf(String timezone) {
        try {
            return timezone == null ? clock.getZone() : ZoneId.of(timezone);
        } catch (RuntimeException e) {
            return clock.getZone();
        }
    }
}
