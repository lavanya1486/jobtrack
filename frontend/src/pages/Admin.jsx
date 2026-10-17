import { useCallback, useEffect, useMemo, useState } from 'react';
import { adminApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import ConfirmDialog from '../components/ConfirmDialog';
import KpiCard from '../components/KpiCard';
import EmptyState, { ErrorState } from '../components/EmptyState';
import { SkeletonCards, SkeletonRows } from '../components/Loading';
import { useToast } from '../components/Toast';
import { formatDate, formatNumber, initials } from '../utils/format';

const PAGE_SIZE = 10;

/**
 * Admin console (ADMIN role only).
 *
 * The route guard here is a convenience: the real boundary is on the server, where every
 * `/api/admin/**` endpoint is annotated `hasRole('ADMIN')` and a normal user gets 403.
 */
export default function Admin() {
  const { user } = useAuth();
  const toast = useToast();

  const [stats, setStats] = useState(null);
  const [users, setUsers] = useState([]);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busyUserId, setBusyUserId] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [statsData, usersData] = await Promise.all([adminApi.stats(), adminApi.users()]);
      setStats(statsData);
      setUsers(usersData || []);
    } catch (requestError) {
      setError(requestError.message || 'Could not load the admin console');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return users;
    return users.filter(
      (row) =>
        row.name?.toLowerCase().includes(term) || row.email?.toLowerCase().includes(term),
    );
  }, [users, search]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const visible = filtered.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  async function changeRole(target, role) {
    setBusyUserId(target.id);
    try {
      await adminApi.changeRole(target.id, role);
      toast.success(`${target.email} is now ${role.toLowerCase()}`);
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not change the role');
    } finally {
      setBusyUserId(null);
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await adminApi.removeUser(pendingDelete.id);
      toast.success(`${pendingDelete.email} deleted`);
      setPendingDelete(null);
      await load();
    } catch (requestError) {
      toast.error(requestError.message || 'Could not delete the user');
    } finally {
      setDeleting(false);
    }
  }

  if (error && !stats) return <ErrorState message={error} onRetry={load} />;

  return (
    <div className="stack">
      <header className="page-header">
        <div>
          <h2 className="page-header__title">Admin console</h2>
          <p className="page-header__subtitle">
            Platform usage and account management. Changing a role revokes that user&apos;s refresh
            tokens, so it takes effect on their next request.
          </p>
        </div>
        <div className="page-header__actions">
          <button type="button" className="btn btn--ghost btn--sm" onClick={load} disabled={loading}>
            {loading ? 'Refreshing…' : '↻ Refresh'}
          </button>
        </div>
      </header>

      {loading && !stats ? (
        <SkeletonCards count={4} />
      ) : (
        <section className="kpi-grid" aria-label="Platform statistics">
          <KpiCard label="Users" value={formatNumber(stats?.totalUsers)} hint={`${formatNumber(stats?.adminUsers)} administrator(s)`} icon="👤" tone="info" />
          <KpiCard label="Enabled accounts" value={formatNumber(stats?.activeUsers)} hint={`${formatNumber(stats?.activeRefreshTokens)} live refresh tokens`} icon="◷" tone="success" />
          <KpiCard
            label="Applications"
            value={formatNumber(stats?.totalApplications)}
            hint={`${formatNumber(stats?.submittedApplications)} submitted · avg ${(stats?.averageApplicationsPerUser ?? 0).toFixed(1)} per user`}
            icon="▤"
            tone="accent"
          />
          <KpiCard
            label="Interviews & reminders"
            value={formatNumber(stats?.totalInterviews)}
            hint={`${formatNumber(stats?.openReminders)} open reminders`}
            icon="▶"
            tone="warning"
          />
        </section>
      )}

      <section className="card card--flush">
        <header className="card__header">
          <div>
            <h2 className="card__title">Users</h2>
            <p className="card__subtitle">{filtered.length} account(s)</p>
          </div>
          <div className="filters__search filters__search--sm">
            <label className="visually-hidden" htmlFor="admin-search">Search users by name or email</label>
            <span className="filters__search-icon" aria-hidden="true">⌕</span>
            <input
              id="admin-search"
              type="search"
              className="input input--search"
              placeholder="Search name or email…"
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
            />
          </div>
        </header>

        {loading ? (
          <SkeletonRows rows={4} columns={6} />
        ) : visible.length === 0 ? (
          <EmptyState
            icon="👤"
            title="No users match that search"
            message="Try a different name or email fragment."
            action={
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => setSearch('')}>
                Clear search
              </button>
            }
          />
        ) : (
          <div className="table-wrap" role="region" aria-label="Users" tabIndex={0}>
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">User</th>
                  <th scope="col">Role</th>
                  <th scope="col">Applications</th>
                  <th scope="col">Interviews</th>
                  <th scope="col">Open reminders</th>
                  <th scope="col">Joined</th>
                  <th scope="col"><span className="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {visible.map((row) => {
                  const isSelf = row.id === user?.id;
                  return (
                    <tr key={row.id} className={isSelf ? 'row--self' : undefined}>
                      <td data-label="User">
                        <span className="user-cell">
                          <span className="avatar avatar--sm" aria-hidden="true">{initials(row.name)}</span>
                          <span>
                            <strong>{row.name}</strong>
                            <small>
                              {row.email} · {row.timezone}
                            </small>
                          </span>
                          {isSelf ? <span className="badge badge--info">you</span> : null}
                        </span>
                      </td>
                      <td data-label="Role">
                        <label className="visually-hidden" htmlFor={`role-${row.id}`}>
                          Role for {row.email}
                        </label>
                        <select
                          id={`role-${row.id}`}
                          className="select-inline"
                          value={row.role}
                          disabled={isSelf || busyUserId === row.id}
                          title={isSelf ? 'You cannot change your own role' : undefined}
                          onChange={(event) => changeRole(row, event.target.value)}
                        >
                          <option value="USER">USER</option>
                          <option value="ADMIN">ADMIN</option>
                        </select>
                      </td>
                      <td data-label="Applications">{formatNumber(row.applications)}</td>
                      <td data-label="Interviews">{formatNumber(row.interviews)}</td>
                      <td data-label="Open reminders">{formatNumber(row.openReminders)}</td>
                      <td data-label="Joined">{formatDate(row.createdAt)}</td>
                      <td data-label="Actions">
                        <div className="row-actions">
                          <button
                            type="button"
                            className="btn btn--danger-ghost btn--xs"
                            disabled={isSelf}
                            title={isSelf ? 'You cannot delete your own account' : undefined}
                            onClick={() => setPendingDelete(row)}
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        <div className="pagination pagination--simple">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={page === 0 || loading}
            onClick={() => setPage(page - 1)}
          >
            ← Previous
          </button>
          <span className="pagination__summary">
            Page {visible.length ? page + 1 : 0} of {pageCount}
          </span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={page + 1 >= pageCount || loading}
            onClick={() => setPage(page + 1)}
          >
            Next →
          </button>
        </div>
      </section>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Delete this user?"
        message={
          pendingDelete
            ? `${pendingDelete.email} and all of their applications, interviews and reminders will be permanently deleted.`
            : ''
        }
        detail="This is a cascade delete at the database level. It cannot be undone."
        busy={deleting}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
