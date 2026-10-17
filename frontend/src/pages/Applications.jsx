import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { applicationsApi } from '../api/endpoints';
import ApplicationForm from '../components/ApplicationForm';
import ConfirmDialog from '../components/ConfirmDialog';
import EmptyState, { ErrorState } from '../components/EmptyState';
import FiltersBar from '../components/FiltersBar';
import Pagination from '../components/Pagination';
import StatusBadge, { TagList } from '../components/StatusBadge';
import { SkeletonRows } from '../components/Loading';
import { useToast } from '../components/Toast';
import { useDebounce } from '../hooks/useDebounce';
import { STATUSES, STATUS_TRANSITIONS } from '../utils/constants';
import { formatDate, formatRelative, formatSalaryRange } from '../utils/format';

const EMPTY_FILTERS = {
  search: '',
  status: [],
  employmentType: [],
  workMode: [],
  location: '',
  tag: '',
  appliedFrom: '',
  appliedTo: '',
  submittedOnly: null,
  overdueOnly: null,
};

/**
 * Applications table.
 *
 * Search, filtering, sorting and pagination are all server-side: the request carries the criteria and
 * the response carries one page plus `totalElements`. That keeps the payload small and the filters
 * consistent no matter how many applications a user tracks.
 */
export default function Applications() {
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [sort, setSort] = useState('newest');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const [data, setData] = useState({ content: [], totalElements: 0, totalPages: 0 });
  const [options, setOptions] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [statusBusy, setStatusBusy] = useState(null);

  const debouncedSearch = useDebounce(filters.search, 350);

  /*
   * The query object is built field by field on purpose: typing in the search box replaces the
   * `filters` object on every keystroke, and depending on it would fire a request per keystroke with
   * the *previous* term - defeating the debounce. Depending on the debounced term plus the individual
   * filter values means one request per settled filter change.
   */
  const queryParams = useMemo(
    () => ({
      search: debouncedSearch,
      status: filters.status,
      employmentType: filters.employmentType,
      workMode: filters.workMode,
      location: filters.location,
      tag: filters.tag,
      appliedFrom: filters.appliedFrom,
      appliedTo: filters.appliedTo,
      submittedOnly: filters.submittedOnly,
      overdueOnly: filters.overdueOnly,
      sort,
      page,
      size,
    }),
    [
      debouncedSearch,
      filters.status,
      filters.employmentType,
      filters.workMode,
      filters.location,
      filters.tag,
      filters.appliedFrom,
      filters.appliedTo,
      filters.submittedOnly,
      filters.overdueOnly,
      sort,
      page,
      size,
    ],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await applicationsApi.list(queryParams);
      setData(result);
    } catch (requestError) {
      setError(requestError.message || 'Could not load applications');
      setData({ content: [], totalElements: 0, totalPages: 0 });
    } finally {
      setLoading(false);
    }
  }, [queryParams]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    applicationsApi
      .filterOptions()
      .then(setOptions)
      .catch(() => setOptions(null));
  }, [data.totalElements]);

  // ?new=1 from the dashboard empty state opens the form straight away.
  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setEditing(null);
      setFormOpen(true);
      searchParams.delete('new');
      setSearchParams(searchParams, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  function updateFilters(patch) {
    setFilters((current) => ({ ...current, ...patch }));
    if (patch.page !== undefined) setPage(patch.page);
  }

  function clearFilters() {
    setFilters(EMPTY_FILTERS);
    setSort('newest');
    setPage(0);
  }

  async function handleSubmit(payload) {
    setSubmitting(true);
    setFormError(null);
    try {
      if (editing) {
        const updated = await applicationsApi.update(editing.id, payload);
        toast.success(`${updated.company} updated`);
      } else {
        const created = await applicationsApi.create(payload);
        toast.success(`${created.company} added to your tracker`);
      }
      setFormOpen(false);
      setEditing(null);
      await load();
    } catch (requestError) {
      setFormError(requestError);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await applicationsApi.remove(pendingDelete.id);
      toast.success(`Deleted ${pendingDelete.company} · ${pendingDelete.jobTitle}`);
      setPendingDelete(null);
      // Stay on a valid page if the last row of the page was deleted.
      if (data.content.length === 1 && page > 0) setPage(page - 1);
      else await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not delete the application');
    } finally {
      setDeleting(false);
    }
  }

  async function handleStatusChange(application, nextStatus) {
    setStatusBusy(application.id);
    try {
      await applicationsApi.changeStatus(application.id, { status: nextStatus });
      toast.success(`${application.company}: ${nextStatus.toLowerCase()}`);
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not change the status');
    } finally {
      setStatusBusy(null);
    }
  }

  const filtersActive =
    Object.values(EMPTY_FILTERS).some((value, index) => {
      const key = Object.keys(EMPTY_FILTERS)[index];
      return Array.isArray(value) ? filters[key]?.length > 0 : Boolean(filters[key]);
    }) || sort !== 'newest';

  return (
    <div className="stack">
      <header className="page-header">
        <div>
          <h2 className="page-header__title">Applications</h2>
          <p className="page-header__subtitle">
            {data.totalElements} record{data.totalElements === 1 ? '' : 's'} matching your filters
          </p>
        </div>
        <div className="page-header__actions">
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => {
              setEditing(null);
              setFormError(null);
              setFormOpen(true);
            }}
          >
            + Track application
          </button>
        </div>
      </header>

      <FiltersBar
        filters={filters}
        onChange={updateFilters}
        options={options}
        sort={sort}
        onSortChange={(value) => {
          setSort(value);
          setPage(0);
        }}
        onClear={clearFilters}
        size={size}
        onSizeChange={(value) => {
          setSize(value);
          setPage(0);
        }}
      />

      <section className="card card--flush">
        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : loading ? (
          <SkeletonRows rows={6} columns={6} />
        ) : data.content.length === 0 ? (
          <EmptyState
            icon="▤"
            title={filtersActive ? 'No applications match these filters' : 'No applications yet'}
            message={
              filtersActive
                ? 'Try widening the date range, clearing the status chips or searching for a different company.'
                : 'Track the roles you have applied to - deadlines, interviews and follow-ups will be generated for you.'
            }
            action={
              filtersActive ? (
                <button type="button" className="btn btn--ghost" onClick={clearFilters}>
                  Clear filters
                </button>
              ) : (
                <button
                  type="button"
                  className="btn btn--primary"
                  onClick={() => {
                    setEditing(null);
                    setFormOpen(true);
                  }}
                >
                  Add your first application
                </button>
              )
            }
          />
        ) : (
          <div className="table-wrap" role="region" aria-label="Applications" tabIndex={0}>
            <table className="table">
              <caption className="visually-hidden">
                Job applications with status, dates and quick actions
              </caption>
              <thead>
                <tr>
                  <th scope="col">Company / role</th>
                  <th scope="col">Status</th>
                  <th scope="col">Location</th>
                  <th scope="col">Applied</th>
                  <th scope="col">Deadline</th>
                  <th scope="col">Salary</th>
                  <th scope="col">Next action</th>
                  <th scope="col">
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((application) => (
                  <tr key={application.id} className={application.deadlineOverdue ? 'row--alert' : undefined}>
                    <td data-label="Company / role">
                      <Link to={`/applications/${application.id}`} className="table__primary-link">
                        <strong>{application.company}</strong>
                        <span>{application.jobTitle}</span>
                      </Link>
                      <TagList tags={application.tags} />
                    </td>
                    <td data-label="Status">
                      <StatusBadge status={application.status} />
                      <label className="visually-hidden" htmlFor={`status-${application.id}`}>
                        Change status for {application.company}
                      </label>
                      <select
                        id={`status-${application.id}`}
                        className="select-inline"
                        value=""
                        disabled={statusBusy === application.id}
                        onChange={(event) => {
                          if (event.target.value) handleStatusChange(application, event.target.value);
                          event.target.value = '';
                        }}
                      >
                        <option value="">Move to…</option>
                        {(STATUS_TRANSITIONS[application.status] || STATUSES).map((status) => (
                          <option key={status} value={status}>
                            {status.charAt(0) + status.slice(1).toLowerCase()}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td data-label="Location">
                      {application.location || '—'}
                      <small>{application.workMode?.toLowerCase()}</small>
                    </td>
                    <td data-label="Applied">
                      {application.appliedAt ? formatDate(application.appliedAt) : '—'}
                      {application.daysSinceApplied !== null && application.daysSinceApplied !== undefined ? (
                        <small>{application.daysSinceApplied}d ago</small>
                      ) : null}
                    </td>
                    <td data-label="Deadline">
                      {application.deadline ? (
                        <>
                          <span className={application.deadlineOverdue ? 'text-danger' : undefined}>
                            {formatDate(application.deadline)}
                          </span>
                          <small>
                            {application.daysUntilDeadline >= 0
                              ? `in ${application.daysUntilDeadline}d`
                              : `${Math.abs(application.daysUntilDeadline)}d overdue`}
                          </small>
                        </>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td data-label="Salary">
                      {formatSalaryRange(
                        application.expectedSalaryMin,
                        application.expectedSalaryMax,
                        application.currency,
                      )}
                    </td>
                    <td data-label="Next action">
                      {application.nextAction || '—'}
                      {application.nextActionDate ? (
                        <small className={application.nextActionDue ? 'text-danger' : undefined}>
                          {formatRelative(application.nextActionDate)}
                        </small>
                      ) : null}
                    </td>
                    <td data-label="Actions">
                      <div className="row-actions">
                        <button
                          type="button"
                          className="btn btn--ghost btn--xs"
                          onClick={() => navigate(`/applications/${application.id}`)}
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="btn btn--ghost btn--xs"
                          onClick={() => {
                            setEditing(application);
                            setFormError(null);
                            setFormOpen(true);
                          }}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="btn btn--danger-ghost btn--xs"
                          onClick={() => setPendingDelete(application)}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <Pagination
          page={data.content.length ? page : 0}
          size={size}
          totalElements={data.totalElements}
          totalPages={data.totalPages}
          onChange={setPage}
          itemName="applications"
        />
      </section>

      <ApplicationForm
        open={formOpen}
        application={editing}
        submitting={submitting}
        error={formError}
        onClose={() => {
          setFormOpen(false);
          setEditing(null);
          setFormError(null);
        }}
        onSubmit={handleSubmit}
      />

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Delete this application?"
        message={
          pendingDelete
            ? `${pendingDelete.company} · ${pendingDelete.jobTitle} will be permanently removed.`
            : ''
        }
        detail="Interview rounds, reminders and the status history for this application are deleted with it. This cannot be undone."
        busy={deleting}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
