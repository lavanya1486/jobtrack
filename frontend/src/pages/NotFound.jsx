import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="not-found">
      <p className="not-found__code">404</p>
      <h2 className="not-found__title">Page not found</h2>
      <p className="not-found__message">
        That route does not exist. If you followed a link from an email or a bookmark, it may be stale.
      </p>
      <Link to="/dashboard" className="btn btn--primary">Back to dashboard</Link>
    </div>
  );
}
