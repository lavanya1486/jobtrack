import { Link } from 'react-router-dom';

/** Split-screen shell for the public authentication pages. */
export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="auth">
      <aside className="auth__aside">
        <Link to="/login" className="auth__brand">
          <span className="brand-mark" aria-hidden="true">JT</span>
          <span>JobTrack</span>
        </Link>
        <div className="auth__pitch">
          <h2>Stop losing track of applications.</h2>
          <ul>
            <li>
              <strong>Every application in one place</strong>
              <span>Company, role, salary, deadline, status and notes.</span>
            </li>
            <li>
              <strong>Know your real conversion rate</strong>
              <span>Response, interview and offer rates computed from your own data.</span>
            </li>
            <li>
              <strong>Never miss a round or a deadline</strong>
              <span>Interview prep and follow-up reminders, generated automatically.</span>
            </li>
          </ul>
        </div>
        <p className="auth__footnote">
          Spring Boot · PostgreSQL · React · JWT with rotating refresh tokens
        </p>
      </aside>

      <main className="auth__main">
        <div className="auth__card">
          <header className="auth__header">
            <h1>{title}</h1>
            {subtitle ? <p>{subtitle}</p> : null}
          </header>
          {children}
          {footer ? <div className="auth__footer">{footer}</div> : null}
        </div>
      </main>
    </div>
  );
}
