import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { interviewsApi } from '../api/endpoints';
import ConfirmDialog from '../components/ConfirmDialog';
import EmptyState, { ErrorState } from '../components/EmptyState';
import InterviewForm from '../components/InterviewForm';
import Pagination from '../components/Pagination';
import { SkeletonRows } from '../components/Loading';
import { useToast } from '../components/Toast';
import { formatDate, formatDateTime, formatRelative, titleCase } from '../utils/format';

const SCOPES = [
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'PAST', label: 'Past' },
  { value: 'ALL', label: 'All rounds' },
];

/** All interview rounds across applications, with outcome capture and rescheduling. */
export default function Interviews() {
  const toast = useToast();
  const [scope, setScope] = useState('UPCOMING');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState({ content: [], totalElements: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [editing, setEditing] = useState(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formError, setFormError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await interviewsApi.list({ scope, page, size });
      setData(result);
    } catch (requestError) {
      setError(requestError.message || 'Could not load interviews');
    } finally {
      setLoading(false);
    }
  }, [scope, page, size]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleSubmit(payload) {
    setSaving(true);
    setFormError(null);
    try {
      await interviewsApi.update(editing.id, payload);
      toast.success('Interview round updated');
      setFormOpen(false);
      setEditing(null);
      await load();
    } catch (requestError) {
      setFormError(requestError);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await interviewsApi.remove(pendingDelete.id);
      toast.success('Interview round removed');
      setPendingDelete(null);
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not remove the round');
    } finally {
      setDeleting(false);
    }
  }

  async function setOutcome(interview, outcome) {
    try {
      await interviewsApi.update(interview.id, {
        roundType: interview.roundType,
        scheduledAt: interview.scheduledAt,
        durationMinutes: interview.durationMinutes,
        locationOrLink: interview.locationOrLink,
        interviewerName: interview.interviewerName,
        prepNotes: interview.prepNotes,
        feedback: interview.feedback,
        outcome,
      });
      toast.success(`${titleCase(interview.roundType)} marked as ${titleCase(outcome)}`);
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not record the outcome');
    }
  }

  return (
    <div className="stack">
      <header className="page-header">
        <div>
          <h2 className="page-header__title">Interviews</h2>
          <p className="page-header__subtitle">
            Rounds are scheduled from an application. Times follow your profile time zone.
          </p>
        </div>
        <div className="page-header__actions">
          <Link to="/applications" className="btn btn--ghost btn--sm">
            Go to applications
          </Link>
        </div>
      </header>

      <div className="tabs" role="tablist" aria-label="Interview scope">
        {SCOPES.map((option) => (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={scope === option.value}
            className={`tab${scope === option.value ? ' is-active' : ''}`}
            onClick={() => {
              setScope(option.value);
              setPage(0);
            }}
          >
            {option.label}
          </button>
        ))}
      </div>

      <section className="card card--flush">
        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : loading ? (
          <SkeletonRows rows={5} columns={5} />
        ) : data.content.length === 0 ? (
          <EmptyState
            icon="📅"
            title={scope === 'UPCOMING' ? 'No upcoming rounds' : 'No interview rounds'}
            message="Open an application and use “Schedule round” to add one - a preparation reminder is created automatically."
            action={<Link to="/applications" className="btn btn--primary btn--sm">Open applications</Link>}
          />
        ) : (
          <ul className="round-list round-list--page">
            {data.content.map((interview) => (
              <li key={interview.id} className={`round${interview.overdue ? ' round--overdue' : ''}`}>
                <div className="round__when">
                  <strong>{formatDateTime(interview.scheduledAt)}</strong>
                  <span>{formatRelative(interview.scheduledAt)}</span>
                  <span className="muted-text">{interview.durationMinutes} min</span>
                </div>
                <div className="round__body">
                  <p className="round__title">
                    {titleCase(interview.roundType)} ·{' '}
                    <Link to={`/applications/${interview.applicationId}`}>{interview.company}</Link>
                  </p>
                  <p className="round__meta">
                    {interview.jobTitle}
                    {interview.interviewerName ? ` · with ${interview.interviewerName}` : ''}
                    {interview.locationOrLink ? (
                      <>
                        {' · '}
                        <a href={interview.locationOrLink} target="_blank" rel="noreferrer">
                          meeting link
                        </a>
                      </>
                    ) : ''}
                  </p>
                  {interview.feedback ? <p className="round__notes">Feedback: {interview.feedback}</p> : null}
                  {interview.prepNotes && !interview.feedback ? (
                    <p className="round__notes">Prep: {interview.prepNotes}</p>
                  ) : null}
                </div>
                <div className="round__actions">
                  <span className={`outcome outcome--${interview.outcome.toLowerCase()}`}>
                    {titleCase(interview.outcome)}
                  </span>
                  {interview.outcome === 'PENDING' ? (
                    <>
                      <button
                        type="button"
                        className="btn btn--ghost btn--xs"
                        onClick={() => setOutcome(interview, 'PASSED')}
                      >
                        Passed
                      </button>
                      <button
                        type="button"
                        className="btn btn--ghost btn--xs"
                        onClick={() => setOutcome(interview, 'FAILED')}
                      >
                        Failed
                      </button>
                    </>
                  ) : null}
                  <button
                    type="button"
                    className="btn btn--ghost btn--xs"
                    onClick={() => {
                      setEditing(interview);
                      setFormError(null);
                      setFormOpen(true);
                    }}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className="btn btn--danger-ghost btn--xs"
                    onClick={() => setPendingDelete(interview)}
                  >
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}

        <Pagination
          page={page}
          size={size}
          totalElements={data.totalElements}
          totalPages={data.totalPages}
          onChange={setPage}
          onSizeChange={(value) => {
            setSize(value);
            setPage(0);
          }}
          itemName="interview rounds"
        />
      </section>

      <InterviewForm
        open={formOpen}
        interview={editing}
        contextLabel={editing ? `${editing.company} · ${editing.jobTitle}` : undefined}
        submitting={saving}
        error={formError}
        onClose={() => {
          setFormOpen(false);
          setEditing(null);
        }}
        onSubmit={handleSubmit}
      />

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Remove this interview round?"
        message={
          pendingDelete
            ? `${titleCase(pendingDelete.roundType)} at ${pendingDelete.company} on ${formatDate(
                pendingDelete.scheduledAt,
              )} will be removed.`
            : ''
        }
        detail="The preparation reminder is removed too. The application itself is kept."
        confirmLabel="Remove round"
        busy={deleting}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
