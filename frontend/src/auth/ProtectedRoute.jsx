import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { Spinner } from '../components/Loading';

/**
 * Route guard.
 *
 * While the stored token is being verified the user sees a spinner instead of being bounced to
 * /login - that flicker is what makes "stay signed in after refresh" feel broken. The original
 * location is preserved so a deep link returns the user where they wanted to go after signing in.
 */
export default function ProtectedRoute({ children, requireAdmin = false }) {
  const { isAuthenticated, isAdmin, bootstrapping } = useAuth();
  const location = useLocation();

  if (bootstrapping) {
    return (
      <div className="route-loader">
        <Spinner label="Checking your session" />
        <p>Restoring your session…</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  if (requireAdmin && !isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
}
