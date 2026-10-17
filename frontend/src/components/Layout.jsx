import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { NAV_ITEMS } from '../utils/constants';
import { initials } from '../utils/format';
import { remindersApi } from '../api/endpoints';

const ICONS = {
  chart: 'M4 19V9m5 10V5m5 14v-7m5 7H2',
  briefcase: 'M3 8h18v11H3zM8 8V5h8v3',
  video: 'M3 7h11v10H3zM14 11l7-4v10l-7-4',
  bell: 'M6 9a6 6 0 1112 0c0 5 2 6 2 6H4s2-1 2-6M10 20h4',
  user: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21c0-4 4-6 8-6s8 2 8 6',
  shield: 'M12 3l8 3v6c0 5-4 8-8 9-4-1-8-4-8-9V6z',
};

function NavIcon({ name }) {
  const path = ICONS[name] || ICONS.chart;
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={path} />
    </svg>
  );
}

/**
 * Authenticated app shell: collapsible sidebar, top bar with the open-reminder badge and the user
 * menu. The sidebar is off-canvas on small screens and toggled by a button in the top bar.
 */
export default function Layout() {
  const { user, logout, isAdmin } = useAuth();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [openReminders, setOpenReminders] = useState(0);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    let cancelled = false;
    remindersApi
      .list({ scope: 'OPEN', size: 1 })
      .then((page) => {
        if (!cancelled) setOpenReminders(page.totalElements || 0);
      })
      .catch(() => {
        /* the badge is decorative; a failure must not break the shell */
      });
    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  const items = isAdmin ? [...NAV_ITEMS, { to: '/admin', label: 'Admin', icon: 'shield' }] : NAV_ITEMS;

  return (
    <div className="app-shell">
      <aside className={`sidebar${mobileNavOpen ? ' is-open' : ''}`} id="primary-nav">
        <div className="sidebar__brand">
          <span className="brand-mark" aria-hidden="true">JT</span>
          <span className="brand-text">
            JobTrack
            <small>Application analytics</small>
          </span>
        </div>

        <nav className="sidebar__nav" aria-label="Main navigation">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `nav-link${isActive ? ' is-active' : ''}`}
            >
              <NavIcon name={item.icon} />
              <span>{item.label}</span>
              {item.to === '/reminders' && openReminders > 0 ? (
                <span className="nav-link__badge">{openReminders}</span>
              ) : null}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar__footer">
          <a
            className="nav-link nav-link--subtle"
            href="/swagger-ui.html"
            target="_blank"
            rel="noreferrer"
          >
            <NavIcon name="chart" />
            <span>API docs</span>
          </a>
        </div>
      </aside>

      {mobileNavOpen ? (
        <div
          className="sidebar-scrim"
          onClick={() => setMobileNavOpen(false)}
          aria-hidden="true"
        />
      ) : null}

      <div className="app-main">
        <header className="topbar">
          <button
            type="button"
            className="icon-button topbar__menu"
            onClick={() => setMobileNavOpen((open) => !open)}
            aria-expanded={mobileNavOpen}
            aria-controls="primary-nav"
            aria-label="Toggle navigation"
          >
            ☰
          </button>

          <div className="topbar__title">
            <h1>{pageTitle(location.pathname)}</h1>
          </div>

          <div className="topbar__actions">
            <div className="user-chip">
              <span className="user-chip__avatar" aria-hidden="true">
                {initials(user?.name)}
              </span>
              <span className="user-chip__meta">
                <strong>{user?.name}</strong>
                <small>
                  {user?.email} · {user?.role === 'ADMIN' ? 'Administrator' : 'Candidate'}
                </small>
              </span>
            </div>
            <button type="button" className="btn btn--ghost btn--sm" onClick={handleLogout}>
              Sign out
            </button>
          </div>
        </header>

        <main className="page" id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function pageTitle(pathname) {
  if (pathname.startsWith('/dashboard')) return 'Dashboard';
  if (pathname.startsWith('/applications/')) return 'Application detail';
  if (pathname.startsWith('/applications')) return 'Applications';
  if (pathname.startsWith('/interviews')) return 'Interviews';
  if (pathname.startsWith('/reminders')) return 'Reminders';
  if (pathname.startsWith('/profile')) return 'Profile & settings';
  if (pathname.startsWith('/admin')) return 'Administration';
  return 'JobTrack';
}
