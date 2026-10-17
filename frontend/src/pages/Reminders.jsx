import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { applicationsApi, remindersApi } from '../api/endpoints';
import ConfirmDialog from '../components/ConfirmDialog';
import EmptyState, { ErrorState } from '../components/EmptyState';
import Modal from '../components/Modal';
import Pagination from '../components/Pagination';
import { Field, SelectField, TextAreaField } from '../components/ApplicationForm';
import { SkeletonRows } from '../components/Loading';
import { useToast } from '../components/Toast';
import { REMINDER_TYPES } from '../utils/constants';
import {
  formatDateTime,
  formatRelative,
  fromDateTimeInputValue,
  titleCase,
  toDateTimeInputValue,
} from '../utils/format';

const SCOPES = [
  { value: 'OPEN', label: 'Open' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'UPCOMING', label: 'Next 7 days' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'ALL', label: 'All' },
];

const EMPTY_FORM = { type: 'FOLLOW_UP', title: '', details: '', dueAt: '', applicationId: '' };

/** Reminders queue: the "what should I do today" page. */
export default function Reminders() {
  const toast = useToast();
  const [scope, setScope] = useState('OPEN');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState({ content: [], totalElements: 0, totalPages: 0 });
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await remindersApi.list({ scope, page, size });
      setData(result);
    } catch (requestError) {
      setError(requestError.message || 'Could not load reminders');
    } finally {
      setLoading(false);
    }
  }, [scope, page, size]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    applicationsApi
      .list({ size: 100, sort: 'company' })
      .then((result) => setApplications(result.content || []))
      .catch(() => setApplications([]));
  }, []);

  const grouped = useMemo(() => groupReminders(data.content), [data.content]);

  function openCreate() {
    setEditing(null);
    setForm({ ...EMPTY_FORM, dueAt: toDateTimeInputValue(defaultDueDate()) });
    setFormErrors({});
    setFormOpen(true);
  }

  function openEdit(reminder) {
    setEditing(reminder);
    setForm({
      type: reminder.type,
      title: reminder.title,
      details: reminder.details || '',
      dueAt: toDateTimeInputValue(reminder.dueAt),
      applicationId: reminder.applicationId ? String(reminder.applicationId) : '',
    });
    setFormErrors({});
    setFormOpen(true);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const errors = validateReminderForm(form);
    setFormErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const payload = {
      type: form.type,
      title: form.title.trim(),
      details: form.details.trim() || null,
      dueAt: fromDateTimeInputValue(form.dueAt),
      applicationId: form.applicationId ? Number(form.applicationId) : null,
    };

    setSaving(true);
    try {
      if (editing) {
        await remindersApi.update(editing.id, payload);
        toast.success('Reminder updated');
      } else {
        await remindersApi.create(payload);
        toast.success('Reminder created');
      }
      setFormOpen(false);
      await load();
    } catch (requestError) {
      setFormErrors({ form: requestError.message });
    } finally {
      setSaving(false);
    }
  }

  async function toggle(reminder) {
    setBusyId(reminder.id);
    try {
      if (reminder.completed) await remindersApi.reopen(reminder.id);
      else await remindersApi.complete(reminder.id);
      toast.success(reminder.completed ? 'Reminder reopened' : 'Nice - reminder completed');
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not update the reminder');
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await remindersApi.remove(pendingDelete.id);
      toast.success('Reminder deleted');
      setPendingDelete(null);
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not delete the reminder');
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="stack">
      <header className="page-header">
        <div>
          <h2 className="page-header__title">Reminders</h2>
          <p className="page-header__subtitle">
            Deadlines, follow-ups and interview prep. Auto-generated reminders are kept in sync when you
            edit an application.
          </p>
        </div>
        <div className="page-header__actions">
          <button type="button" className="btn btn--primary" onClick={openCreate}>
            + New reminder
          </button>
        </div>
      </header>

      <div className="tabs" role="tablist" aria-label="Reminder scope">
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
          <SkeletonRows rows={5} columns={4} />
        ) : data.content.length === 0 ? (
          <EmptyState
            icon="🔔"
            title={scope === 'OVERDUE' ? 'Nothing overdue' : 'No reminders here'}
            message={
              scope === 'OVERDUE'
                ? 'Everything is up to date. Reminders turn red here once their due time passes.'
                : 'Create one manually, or add a deadline / next action to an application and JobTrack will generate it for you.'
            }
            action={
              <button type="button" className="btn btn--primary btn--sm" onClick={openCreate}>
                New reminder
              </button>
            }
          />
        ) : (
          Object.entries(grouped).map(([group, items]) =>
            items.length === 0 ? null : (
              <div key={group} className="reminder-group">
                <h3 className={`reminder-group__title${group === 'Overdue' ? ' text-danger' : ''}`}>
                  {group} <span className="reminder-group__count">{items.length}</span>
                </h3>
                <ul className="reminder-list">
                  {items.map((reminder) => (
                    <li
                      key={reminder.id}
                      className={`reminder${reminder.completed ? ' reminder--done' : ''}${
                        reminder.overdue && !reminder.completed ? ' reminder--overdue' : ''
                      }`}
                    >
                      <label className="reminder__check">
                        <input
                          type="checkbox"
                          checked={reminder.completed}
                          disabled={busyId === reminder.id}
                          onChange={() => toggle(reminder)}
                        />
                        <span className="visually-hidden">
                          {reminder.completed ? 'Reopen' : 'Complete'} {reminder.title}
                        </span>
                      </label>
                      <div className="reminder__body">
                        <p className="reminder__title">{reminder.title}</p>
                        <p className="reminder__meta">
                          <span className={`reminder__type reminder__type--${reminder.type.toLowerCase()}`}>
                            {titleCase(reminder.type)}
                          </span>
                          {reminder.company ? (
                            <Link to={`/applications/${reminder.applicationId}`}>
                              {reminder.company} · {reminder.jobTitle}
                            </Link>
                          ) : null}
                          <span>{formatDateTime(reminder.dueAt)}</span>
                          <span>{formatRelative(reminder.dueAt)}</span>
                          {reminder.source === 'AUTO' ? <span className="muted-text">auto</span> : null}
                        </p>
                        {reminder.details ? <p className="reminder__details">{reminder.details}</p> : null}
                      </div>
                      <div className="reminder__actions">
                        <button type="button" className="btn btn--ghost btn--xs" onClick={() => openEdit(reminder)}>
                          Edit
                        </button>
                        <button
                          type="button"
                          className="btn btn--danger-ghost btn--xs"
                          onClick={() => setPendingDelete(reminder)}
                        >
                          Delete
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            ),
          )
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
          itemName="reminders"
        />
      </section>

      <Modal
        open={formOpen}
        onClose={saving ? undefined : () => setFormOpen(false)}
        title={editing ? 'Edit reminder' : 'New reminder'}
        description="Manual reminders sit next to the generated ones and never get overwritten by the scheduler."
        size="md"
        footer={
          <>
            <button type="button" className="btn btn--ghost" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </button>
            <button type="submit" form="reminder-form" className="btn btn--primary" disabled={saving}>
              {saving ? 'Saving…' : editing ? 'Save reminder' : 'Create reminder'}
            </button>
          </>
        }
      >
        <form id="reminder-form" className="form" onSubmit={handleSubmit} noValidate>
          {formErrors.form ? (
            <p className="form__banner" role="alert">{formErrors.form}</p>
          ) : null}
          <div className="form__grid form__grid--two">
            <SelectField
              id="reminder-type"
              label="Type"
              value={form.type}
              onChange={(value) => setForm({ ...form, type: value })}
              options={REMINDER_TYPES.map((type) => ({ value: type, label: titleCase(type) }))}
            />
            <Field
              id="reminder-due"
              label="Due at"
              type="datetime-local"
              value={form.dueAt}
              onChange={(value) => setForm({ ...form, dueAt: value })}
              error={formErrors.dueAt}
            />
            <Field
              id="reminder-title"
              label="Title"
              className="span-2"
              value={form.title}
              onChange={(value) => setForm({ ...form, title: value })}
              error={formErrors.title}
              placeholder="e.g. Follow up with the recruiter"
            />
            <SelectField
              id="reminder-application"
              label="Application (optional)"
              className="span-2"
              value={form.applicationId}
              onChange={(value) => setForm({ ...form, applicationId: value })}
              options={applications.map((application) => ({
                value: String(application.id),
                label: `${application.company} · ${application.jobTitle}`,
              }))}
            />
            <TextAreaField
              id="reminder-details"
              label="Details"
              className="span-2"
              rows={3}
              value={form.details}
              onChange={(value) => setForm({ ...form, details: value })}
              error={formErrors.details}
            />
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Delete this reminder?"
        message={pendingDelete ? `“${pendingDelete.title}” will be removed.` : ''}
        detail="Auto-generated reminders may be recreated by the scheduler if the underlying deadline or interview still exists."
        busy={deleting}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}

export function validateReminderForm(form) {
  const errors = {};
  if (!form.title || form.title.trim().length < 3) errors.title = 'Give the reminder a title (3+ characters)';
  else if (form.title.trim().length > 200) errors.title = 'At most 200 characters';
  if (!form.dueAt) errors.dueAt = 'Pick a due date and time';
  else if (!fromDateTimeInputValue(form.dueAt)) errors.dueAt = 'Invalid date/time';
  if (form.details && form.details.length > 500) errors.details = 'At most 500 characters';
  return errors;
}

function defaultDueDate() {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  date.setHours(9, 0, 0, 0);
  return date.toISOString();
}

/** Buckets a page of reminders into Overdue / Today / Tomorrow / This week / Later / Completed. */
export function groupReminders(reminders = []) {
  const groups = { Overdue: [], Today: [], Tomorrow: [], 'This week': [], Later: [], Completed: [] };
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  reminders.forEach((reminder) => {
    if (reminder.completed) {
      groups.Completed.push(reminder);
      return;
    }
    const due = new Date(reminder.dueAt);
    const dayDiff = Math.floor((due - startOfToday) / 86400000);
    if (due < now) groups.Overdue.push(reminder);
    else if (dayDiff <= 0) groups.Today.push(reminder);
    else if (dayDiff === 1) groups.Tomorrow.push(reminder);
    else if (dayDiff <= 7) groups['This week'].push(reminder);
    else groups.Later.push(reminder);
  });

  return groups;
}
