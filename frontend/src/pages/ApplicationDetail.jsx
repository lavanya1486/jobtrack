import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { applicationsApi, interviewsApi, remindersApi } from '../api/endpoints';
import ApplicationForm from '../components/ApplicationForm';
import ConfirmDialog from '../components/ConfirmDialog';
import InterviewForm from '../components/InterviewForm';
import EmptyState, { ErrorState } from '../components/EmptyState';
import StatusBadge, { TagList } from '../components/StatusBadge';
import { Spinner } from '../components/Loading';
import { useToast } from '../components/Toast';
import { STATUS_TRANSITIONS } from '../utils/constants';
import {
  formatDate,
  formatDateTime,
  formatRelative,
  formatSalaryRange,
  titleCase,
} from '../utils/format';

/**
 * Application detail: everything about one application, its interview rounds, its status timeline
 * and its reminders - loaded in one request for the main data.
 */
export default function ApplicationDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();

  const [detail, setDetail] = useState(null);
  const [reminders, setReminders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [editOpen, setEditOpen] = useState(false);
  const [editError, setEditError] = useState(null);
  const [saving, setSaving] = useState(false);

  const [interviewFormOpen, setInterviewFormOpen] = useState(false);
  const [editingInterview, setEditingInterview] = useState(null);
  const [interviewError, setInterviewError] = useState(null);
  const [interviewBusy, setInterviewBusy] = useState(false);

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [pendingInterviewDelete, setPendingInterviewDelete] = useState(null);

  const [statusDraft, setStatusDraft] = useState({ status: '', note: '' });
  const [statusBusy, setStatusBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await applicationsApi.get(id);
      setDetail(result);
      remindersApi
        .list({ scope: 'ALL', size: 100 })
        .then((page) =>
          setReminders(
            (page.content || []).filter((reminder) => String(reminder.applicationId) === String(id)),
          ),
        )
        .catch(() => setReminders([]));
    } catch (requestError) {
      setError(requestError.status === 404 ? 'This application does not exist.' : requestError.message);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const application = detail?.application;
  const allowedTransitions = useMemo(
    () => (application ? STATUS_TRANSITIONS[application.status] || [] : []),
    [application],
  );

  async function handleSave(payload) {
    setSaving(true);
    setEditError(null);
    try {
      await applicationsApi.update(id, payload);
      toast.success('Application updated');
      setEditOpen(false);
      await load();
    } catch (requestError) {
      setEditError(requestError);
    } finally {
      setSaving(false);
    }
  }

  async function handleStatusSubmit(event) {
    event.preventDefault();
    if (!statusDraft.status) return;
    setStatusBusy(true);
    try {
      await applicationsApi.changeStatus(id, {
        status: statusDraft.status,
        note: statusDraft.note.trim() || null,
      });
      toast.success(`Status moved to ${titleCase(statusDraft.status)}`);
      setStatusDraft({ status: '', note: '' });
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not change the status');
    } finally {
      setStatusBusy(false);
    }
  }

  async function handleInterviewSubmit(payload) {
    setInterviewBusy(true);
    setInterviewError(null);
    try {
      if (editingInterview) {
        await interviewsApi.update(editingInterview.id, payload);
        toast.success('Interview round updated');
      } else {
        await applicationsApi.addInterview(id, payload);
        toast.success('Interview round scheduled - a prep reminder was created');
      }
      setInterviewFormOpen(false);
      setEditingInterview(null);
      await load();
    } catch (requestError) {
      setInterviewError(requestError);
    } finally {
      setInterviewBusy(false);
    }
  }

  async function handleDelete() {
    setDeleting(true);
    try {
      await applicationsApi.remove(id);
      toast.success('Application deleted');
      navigate('/applications', { replace: true });
    } catch (requestError) {
      toast.error(requestError.message || 'Could not delete the application');
      setDeleting(false);
    }
  }

  async function handleDeleteInterview() {
    if (!pendingInterviewDelete) return;
    setDeleting(true);
    try {
      await interviewsApi.remove(pendingInterviewDelete.id);
      toast.success('Interview round removed');
      setPendingInterviewDelete(null);
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not remove the round');
    } finally {
      setDeleting(false);
    }
  }

  async function toggleReminder(reminder) {
    try {
      if (reminder.completed) await remindersApi.reopen(reminder.id);
      else await remindersApi.complete(reminder.id);
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not update the reminder');
    }
  }

  if (loading && !detail) {
    return (
      <div className="route-loader">
        <Spinner label="Loading application" />
        <p>Loading application…</p>
      </div>
    );
  }

  if (error || !application) {
    return <ErrorState message={error || 'Application not found'} onRetry={load} />;
  }

  return (
    <div className="stack">
      <nav aria-label="Breadcrumb" className="breadcrumb">
        <Link to="/applications">Applications</Link>
        <span aria-hidden="true">/</span>
        <span>{application.company}</span>
      </nav>

      <header className="page-header page-header--detail">
        <div>
          <div className="detail-title">
            <h2 className="page-header__title">{application.jobTitle}</h2>
            <StatusBadge status={application.status} />
          </div>
          <p className="page-header__subtitle">
            {application.company}
            {application.location ? ` · ${application.location}` : ''} ·{' '}
            {titleCase(application.workMode)} · {titleCase(application.employmentType)}
            {application.source ? ` · via ${application.source}` : ''}
          </p>
          <TagList tags={application.tags} />
        </div>
        <div className="page-header__actions">
          {application.jobUrl ? (
            <a className="btn btn--ghost btn--sm" href={application.jobUrl} target="_blank" rel="noreferrer">
              Open posting ↗
            </a>
          ) : null}
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => {
              setEditError(null);
              setEditOpen(true);
            }}
          >
            Edit
          </button>
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => {
              setEditingInterview(null);
              setInterviewError(null);
              setInterviewFormOpen(true);
            }}
          >
            + Schedule round
          </button>
          <button type="button" className="btn btn--danger-ghost btn--sm" onClick={() => setDeleteOpen(true)}>
            Delete
          </button>
        </div>
      </header>

      <div className="detail-grid">
        <div className="stack">
          <section className="card">
            <header className="card__header">
              <h2 className="card__title">Application details</h2>
            </header>
            <dl className="detail-list">
              <DetailItem label="Status" value={titleCase(application.status)} />
              <DetailItem
                label="Applied on"
                value={application.appliedAt ? `${formatDate(application.appliedAt)} (${formatRelative(application.appliedAt)})` : 'Not submitted yet'}
              />
              <DetailItem
                label="Deadline"
                value={
                  application.deadline ? (
                    <span className={application.deadlineOverdue ? 'text-danger' : undefined}>
                      {formatDate(application.deadline)} ({formatRelative(application.deadline)})
                    </span>
                  ) : (
                    '—'
                  )
                }
              />
              <DetailItem
                label="Next action"
                value={
                  application.nextAction
                    ? `${application.nextAction}${
                        application.nextActionDate ? ` · ${formatDate(application.nextActionDate)}` : ''
                      }`
                    : '—'
                }
              />
              <DetailItem
                label="Expected salary"
                value={formatSalaryRange(
                  application.expectedSalaryMin,
                  application.expectedSalaryMax,
                  application.currency,
                )}
              />
              <DetailItem
                label="Employer response"
                value={
                  application.firstResponseAt
                    ? formatDateTime(application.firstResponseAt)
                    : 'No response recorded'
                }
              />
              <DetailItem label="Interview rounds" value={String(detail.interviews?.length || 0)} />
              <DetailItem label="Open reminders" value={String(application.openReminderCount ?? 0)} />
            </dl>

            {application.jobDescription ? (
              <div className="detail-block">
                <h3>Job description</h3>
                <p className="pre-wrap">{application.jobDescription}</p>
              </div>
            ) : null}

            {application.notes ? (
              <div className="detail-block">
                <h3>Notes</h3>
                <p className="pre-wrap">{application.notes}</p>
              </div>
            ) : null}
          </section>

          <section className="card">
            <header className="card__header">
              <div>
                <h2 className="card__title">Interview rounds</h2>
                <p className="card__subtitle">
                  Scheduling a round advances an APPLIED/ASSESSMENT application to INTERVIEW automatically.
                </p>
              </div>
            </header>

            {detail.interviews?.length ? (
              <ul className="round-list">
                {detail.interviews.map((interview) => (
                  <li key={interview.id} className="round">
                    <div className="round__when">
                      <strong>{formatDateTime(interview.scheduledAt)}</strong>
                      <span>{formatRelative(interview.scheduledAt)}</span>
                      <span className="muted-text">{interview.durationMinutes} min</span>
                    </div>
                    <div className="round__body">
                      <p className="round__title">{titleCase(interview.roundType)}</p>
                      <p className="round__meta">
                        {interview.interviewerName ? `With ${interview.interviewerName}` : 'Interviewer not set'}
                        {interview.locationOrLink ? (
                          <>
                            {' · '}
                            <a href={interview.locationOrLink} target="_blank" rel="noreferrer">
                              meeting link
                            </a>
                          </>
                        ) : null}
                      </p>
                      {interview.prepNotes ? <p className="round__notes">Prep: {interview.prepNotes}</p> : null}
                      {interview.feedback ? <p className="round__notes">Feedback: {interview.feedback}</p> : null}
                    </div>
                    <div className="round__actions">
                      <span className={`outcome outcome--${interview.outcome.toLowerCase()}`}>
                        {titleCase(interview.outcome)}
                      </span>
                      <button
                        type="button"
                        className="btn btn--ghost btn--xs"
                        onClick={() => {
                          setEditingInterview(interview);
                          setInterviewError(null);
                          setInterviewFormOpen(true);
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn btn--danger-ghost btn--xs"
                        onClick={() => setPendingInterviewDelete(interview)}
                      >
                        Remove
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState
                icon="📅"
                title="No rounds yet"
                message="Schedule a phone screen, assessment or technical round and a preparation reminder is created automatically."
                action={
                  <button
                    type="button"
                    className="btn btn--primary btn--sm"
                    onClick={() => {
                      setEditingInterview(null);
                      setInterviewFormOpen(true);
                    }}
                  >
                    Schedule a round
                  </button>
                }
              />
            )}
          </section>
        </div>

        <div className="stack">
          <section className="card">
            <header className="card__header">
              <h2 className="card__title">Move to another status</h2>
            </header>
            {allowedTransitions.length === 0 ? (
              <p className="muted-text">No further transitions are available from this status.</p>
            ) : (
              <form className="form" onSubmit={handleStatusSubmit}>
                <div className="field">
                  <label className="field__label" htmlFor="status-target">New status</label>
                  <select
                    id="status-target"
                    className="input"
                    value={statusDraft.status}
                    onChange={(event) => setStatusDraft({ ...statusDraft, status: event.target.value })}
                  >
                    <option value="">Choose a status…</option>
                    {allowedTransitions.map((status) => (
                      <option key={status} value={status}>
                        {titleCase(status)}
                      </option>
                    ))}
                  </select>
                  <p className="field__hint">
                    Impossible transitions (for example SAVED → OFFER) are rejected by the API.
                  </p>
                </div>
                <div className="field">
                  <label className="field__label" htmlFor="status-note">Note (optional)</label>
                  <textarea
                    id="status-note"
                    rows={2}
                    className="input"
                    value={statusDraft.note}
                    onChange={(event) => setStatusDraft({ ...statusDraft, note: event.target.value })}
                    placeholder="Recruiter moved me to the technical round"
                  />
                </div>
                <button
                  type="submit"
                  className="btn btn--primary btn--block"
                  disabled={!statusDraft.status || statusBusy}
                >
                  {statusBusy ? 'Updating…' : 'Update status'}
                </button>
              </form>
            )}
          </section>

          <section className="card">
            <header className="card__header">
              <h2 className="card__title">Status timeline</h2>
              <p className="card__subtitle">{detail.history?.length || 0} entries</p>
            </header>
            {detail.history?.length ? (
              <ol className="timeline">
                {detail.history.map((entry) => (
                  <li key={entry.id} className="timeline__item">
                    <span className="timeline__dot" aria-hidden="true" />
                    <div>
                      <p className="timeline__title">
                        {entry.fromStatus ? `${titleCase(entry.fromStatus)} → ` : ''}
                        <strong>{titleCase(entry.toStatus)}</strong>
                      </p>
                      <p className="timeline__meta">
                        {formatDateTime(entry.changedAt)} · {formatRelative(entry.changedAt)}
                      </p>
                      {entry.note ? <p className="timeline__note">{entry.note}</p> : null}
                    </div>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="muted-text">No status changes recorded yet.</p>
            )}
          </section>

          <section className="card">
            <header className="card__header">
              <h2 className="card__title">Reminders</h2>
              <Link to="/reminders" className="btn btn--ghost btn--xs">All reminders</Link>
            </header>
            {reminders.length ? (
              <ul className="reminder-list">
                {reminders.map((reminder) => (
                  <li key={reminder.id} className={`reminder${reminder.completed ? ' reminder--done' : ''}`}>
                    <label className="reminder__check">
                      <input
                        type="checkbox"
                        checked={reminder.completed}
                        onChange={() => toggleReminder(reminder)}
                      />
                      <span className="visually-hidden">
                        {reminder.completed ? 'Reopen' : 'Complete'} {reminder.title}
                      </span>
                    </label>
                    <div className="reminder__body">
                      <p className="reminder__title">{reminder.title}</p>
                      <p className="reminder__meta">
                        {titleCase(reminder.type)} · {formatDateTime(reminder.dueAt)} ·{' '}
                        {reminder.overdue && !reminder.completed ? (
                          <span className="text-danger">overdue</span>
                        ) : (
                          formatRelative(reminder.dueAt)
                        )}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted-text">
                Deadlines and next actions generate reminders automatically. Nothing is pending here.
              </p>
            )}
          </section>
        </div>
      </div>

      <ApplicationForm
        open={editOpen}
        application={application}
        submitting={saving}
        error={editError}
        onClose={() => setEditOpen(false)}
        onSubmit={handleSave}
      />

      <InterviewForm
        open={interviewFormOpen}
        interview={editingInterview}
        contextLabel={`${application.company} · ${application.jobTitle}`}
        submitting={interviewBusy}
        error={interviewError}
        onClose={() => {
          setInterviewFormOpen(false);
          setEditingInterview(null);
        }}
        onSubmit={handleInterviewSubmit}
      />

      <ConfirmDialog
        open={deleteOpen}
        title="Delete this application?"
        message={`${application.company} · ${application.jobTitle} will be permanently removed.`}
        detail={`${detail.interviews?.length || 0} interview round(s), ${reminders.length} reminder(s) and the full status history are deleted with it.`}
        busy={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteOpen(false)}
      />

      <ConfirmDialog
        open={Boolean(pendingInterviewDelete)}
        title="Remove this interview round?"
        message={
          pendingInterviewDelete
            ? `${titleCase(pendingInterviewDelete.roundType)} on ${formatDateTime(pendingInterviewDelete.scheduledAt)} will be removed.`
            : ''
        }
        detail="The preparation reminder for this round is removed too. The application status is not changed."
        confirmLabel="Remove round"
        busy={deleting}
        onConfirm={handleDeleteInterview}
        onCancel={() => setPendingInterviewDelete(null)}
      />
    </div>
  );
}

function DetailItem({ label, value }) {
  return (
    <div className="detail-list__item">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
