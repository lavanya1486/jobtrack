import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { analyticsApi, interviewsApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import KpiCard from '../components/KpiCard';
import EmptyState, { ErrorState } from '../components/EmptyState';
import { SkeletonCards } from '../components/Loading';
import MonthlyTrendChart from '../components/charts/MonthlyTrendChart';
import StatusDistributionChart from '../components/charts/StatusDistributionChart';
import FunnelChart from '../components/charts/FunnelChart';
import StatusBadge from '../components/StatusBadge';
import { formatDateTime, formatNumber, formatPercent, formatRelative, titleCase } from '../utils/format';

const MONTH_OPTIONS = [3, 6, 12];

/**
 * Dashboard: KPIs, monthly trend, status breakdown, funnel and the "needs attention" feed.
 *
 * Every number comes from the API (which aggregates in PostgreSQL) - nothing is computed from a
 * client-side copy of the data, so the dashboard cannot drift from the database.
 */
export default function Dashboard() {
  const { user } = useAuth();
  const [months, setMonths] = useState(6);
  const [summary, setSummary] = useState(null);
  const [trends, setTrends] = useState(null);
  const [events, setEvents] = useState([]);
  const [interviews, setInterviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [summaryData, trendData, eventData, interviewData] = await Promise.all([
        analyticsApi.summary(),
        analyticsApi.trends(months),
        analyticsApi.upcoming({ days: 21, limit: 8 }),
        interviewsApi.upcoming(5),
      ]);
      setSummary(summaryData);
      setTrends(trendData);
      setEvents(eventData);
      setInterviews(interviewData);
    } catch (requestError) {
      setError(requestError.message || 'Could not load the dashboard');
    } finally {
      setLoading(false);
    }
  }, [months]);

  useEffect(() => {
    load();
  }, [load]);

  if (error && !summary) {
    return <ErrorState message={error} onRetry={load} />;
  }

  if (loading && !summary) {
    return (
      <div className="stack">
        <SkeletonCards count={4} />
        <SkeletonCards count={3} />
      </div>
    );
  }

  const firstName = user?.name?.split(' ')[0] || 'there';
  const hasData = summary && summary.totalApplications > 0;

  return (
    <div className="stack">
      <header className="page-header">
        <div>
          <h2 className="page-header__title">Hello {firstName} 👋</h2>
          <p className="page-header__subtitle">
            {hasData
              ? `${formatNumber(summary.submittedApplications)} submitted applications · ${formatNumber(
                  summary.activeApplications,
                )} still active · ${formatNumber(summary.upcomingInterviews)} interview${
                  summary.upcomingInterviews === 1 ? '' : 's'
                } upcoming`
              : 'Add your first application to start building your funnel.'}
          </p>
        </div>
        <div className="page-header__actions">
          <div className="segmented" role="group" aria-label="Trend window">
            {MONTH_OPTIONS.map((option) => (
              <button
                key={option}
                type="button"
                className={`segmented__button${months === option ? ' is-active' : ''}`}
                onClick={() => setMonths(option)}
                aria-pressed={months === option}
              >
                {option}M
              </button>
            ))}
          </div>
          <button type="button" className="btn btn--ghost btn--sm" onClick={load} disabled={loading}>
            {loading ? 'Refreshing…' : '↻ Refresh'}
          </button>
          <Link to="/applications" className="btn btn--primary btn--sm">View applications</Link>
        </div>
      </header>

      {!hasData ? (
        <EmptyState
          icon="◇"
          title="No applications tracked yet"
          message="Add the roles you have applied to and JobTrack will compute your response, interview and offer rates from real data."
          action={<Link to="/applications?new=1" className="btn btn--primary">Add your first application</Link>}
        />
      ) : (
        <>
          <section className="kpi-grid" aria-label="Key metrics">
            <KpiCard
              label="Total applications"
              value={formatNumber(summary.totalApplications)}
              hint={`${formatNumber(summary.savedDrafts)} draft${summary.savedDrafts === 1 ? '' : 's'} not submitted`}
              icon="▤"
              loading={loading}
            />
            <KpiCard
              label="Active"
              value={formatNumber(summary.activeApplications)}
              hint={`${formatNumber(summary.closedApplications)} closed`}
              tone="info"
              icon="◷"
              loading={loading}
            />
            <KpiCard
              label="Response rate"
              value={formatPercent(summary.responseRate)}
              hint={`${formatNumber(summary.respondedApplications)} of ${formatNumber(
                summary.submittedApplications,
              )} submitted got a reply`}
              tone="success"
              icon="✉"
              loading={loading}
            />
            <KpiCard
              label="Interview rate"
              value={formatPercent(summary.interviewRate)}
              hint={`${formatNumber(summary.reachedInterview)} reached an interview`}
              tone="accent"
              icon="◎"
              loading={loading}
            />
            <KpiCard
              label="Offer rate"
              value={formatPercent(summary.offerRate)}
              hint={`${formatNumber(summary.offersReceived)} offer${summary.offersReceived === 1 ? '' : 's'} · ${formatNumber(summary.rejections)} rejected`}
              tone="warning"
              icon="★"
              loading={loading}
            />
            <KpiCard
              label="Upcoming interviews"
              value={formatNumber(summary.upcomingInterviews)}
              hint={`${formatNumber(summary.passedInterviews)} passed · ${formatNumber(summary.failedInterviews)} failed`}
              tone="info"
              icon="▶"
              loading={loading}
            />
            <KpiCard
              label="Needs attention"
              value={formatNumber(summary.overdueApplications + summary.dueNextActions)}
              hint={`${formatNumber(summary.overdueApplications)} overdue deadline${
                summary.overdueApplications === 1 ? '' : 's'
              } · ${formatNumber(summary.dueNextActions)} next action${
                summary.dueNextActions === 1 ? '' : 's'
              } due`}
              tone={summary.overdueApplications > 0 ? 'danger' : 'default'}
              icon="!"
              loading={loading}
            />
            <KpiCard
              label="Open reminders"
              value={formatNumber(summary.openReminders)}
              hint={
                summary.overdueReminders > 0
                  ? `${formatNumber(summary.overdueReminders)} overdue`
                  : summary.averageDaysToFirstResponse !== null
                    ? `Avg. first reply in ${summary.averageDaysToFirstResponse} days`
                    : 'All up to date'
              }
              tone={summary.overdueReminders > 0 ? 'danger' : 'default'}
              icon="🔔"
              loading={loading}
            />
          </section>

          <MonthlyTrendChart data={trends?.monthly || []} loading={loading} />

          <div className="grid-2">
            <StatusDistributionChart
              slices={summary.statusDistribution || []}
              total={summary.totalApplications}
              loading={loading}
            />
            <FunnelChart stages={summary.funnel || []} loading={loading} />
          </div>

          <div className="grid-2">
            <section className="card">
              <header className="card__header">
                <div>
                  <h2 className="card__title">Needs attention</h2>
                  <p className="card__subtitle">Interviews, deadlines, next actions and reminders</p>
                </div>
                <Link to="/reminders" className="btn btn--ghost btn--sm">All reminders</Link>
              </header>
              {events.length === 0 ? (
                <EmptyState
                  icon="✓"
                  title="Nothing due"
                  message="No deadlines, interviews or follow-ups in the next three weeks."
                />
              ) : (
                <ul className="event-list">
                  {events.map((event, index) => (
                    <li key={`${event.kind}-${event.id || event.applicationId}-${index}`}
                      className={`event${event.overdue ? ' event--overdue' : ''}`}>
                      <span className={`event__kind event__kind--${event.kind.toLowerCase()}`}>
                        {titleCase(event.kind)}
                      </span>
                      <div className="event__body">
                        <p className="event__title">{event.title}</p>
                        <p className="event__meta">
                          {event.company ? `${event.company} · ` : ''}
                          {event.jobTitle ? `${event.jobTitle} · ` : ''}
                          {event.label}
                        </p>
                      </div>
                      {event.applicationId ? (
                        <Link className="event__link" to={`/applications/${event.applicationId}`}>
                          Open
                        </Link>
                      ) : (
                        <span className="event__link event__link--static">
                          {event.daysAway === 0 ? 'Today' : event.overdue ? 'Overdue' : `In ${event.daysAway}d`}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="card">
              <header className="card__header">
                <div>
                  <h2 className="card__title">Next interviews</h2>
                  <p className="card__subtitle">Times shown in {user?.timezone || 'your local'} time</p>
                </div>
                <Link to="/interviews" className="btn btn--ghost btn--sm">All rounds</Link>
              </header>
              {interviews.length === 0 ? (
                <EmptyState
                  icon="📅"
                  title="No rounds scheduled"
                  message="Schedule an interview round from any application to see it here."
                />
              ) : (
                <ul className="interview-list">
                  {interviews.map((interview) => (
                    <li key={interview.id} className="interview-item">
                      <div className="interview-item__date" aria-hidden="true">
                        <strong>{new Date(interview.scheduledAt).getDate()}</strong>
                        <span>
                          {new Date(interview.scheduledAt).toLocaleString('en-GB', { month: 'short' })}
                        </span>
                      </div>
                      <div className="interview-item__body">
                        <p className="interview-item__title">
                          {titleCase(interview.roundType)} · {interview.company}
                        </p>
                        <p className="interview-item__meta">
                          {interview.jobTitle} · {formatDateTime(interview.scheduledAt)} (
                          {formatRelative(interview.scheduledAt)})
                          {interview.interviewerName ? ` · ${interview.interviewerName}` : ''}
                        </p>
                      </div>
                      <div className="interview-item__actions">
                        <StatusBadge status="INTERVIEW" size="sm" />
                        {interview.locationOrLink ? (
                          <a
                            className="btn btn--ghost btn--xs"
                            href={interview.locationOrLink}
                            target="_blank"
                            rel="noreferrer"
                          >
                            Join
                          </a>
                        ) : null}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          {/* Only worth showing once a company appears more than once. */}
          {summary.topCompanies?.some((company) => company.applications > 1) ? (
            <section className="card">
              <header className="card__header">
                <div>
                  <h2 className="card__title">Where you are applying most</h2>
                  <p className="card__subtitle">Top companies by application count</p>
                </div>
              </header>
              <div className="company-strip">
                {summary.topCompanies.map((company) => (
                  <div key={company.company} className="company-chip">
                    <strong>{company.company}</strong>
                    <span>
                      {company.applications} app{company.applications === 1 ? '' : 's'} ·{' '}
                      {company.interviews} interview{company.interviews === 1 ? '' : 's'} ·{' '}
                      {company.offers} offer{company.offers === 1 ? '' : 's'}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          ) : null}
        </>
      )}
    </div>
  );
}
