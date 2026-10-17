import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { userApi } from '../api/endpoints';
import { AuthProvider } from '../auth/AuthContext';
import ProtectedRoute from '../auth/ProtectedRoute';

vi.mock('../api/endpoints', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), logout: vi.fn() },
  userApi: { me: vi.fn(), updateMe: vi.fn(), changePassword: vi.fn() },
  remindersApi: { list: vi.fn().mockResolvedValue({ totalElements: 0 }) },
}));

const USER = {
  id: 1,
  name: 'Asha Verma',
  email: 'asha@example.com',
  role: 'USER',
  timezone: 'Asia/Kolkata',
  emailRemindersEnabled: false,
};

function FromProbe() {
  const location = useLocation();
  return <p data-testid="from">redirect:{location.state?.from || 'none'}</p>;
}

function renderRoutes({ initialEntries = ['/dashboard'], requireAdmin = false } = {}) {
  return render(
    <AuthProvider>
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }} initialEntries={initialEntries}>
        <Routes>
          <Route
            path="/login"
            element={
              <div>
                <h1>Login page</h1>
                <FromProbe />
              </div>
            }
          />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <h1>Dashboard stub</h1>
              </ProtectedRoute>
            }
          />
          <Route
            path="/applications"
            element={
              <ProtectedRoute requireAdmin={requireAdmin}>
                <h1>Applications stub</h1>
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <ProtectedRoute requireAdmin>
                <h1>Admin stub</h1>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

function signInStoringSession(user = USER) {
  localStorage.setItem('jobtrack.accessToken', 'access.jwt.token');
  localStorage.setItem('jobtrack.refreshToken', 'refresh-token');
  localStorage.setItem('jobtrack.user', JSON.stringify(user));
}

describe('route protection and session restore', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    userApi.me.mockResolvedValue(USER);
  });

  it('sends an anonymous visitor to the login page', async () => {
    renderRoutes();
    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Dashboard stub')).not.toBeInTheDocument();
    expect(userApi.me).not.toHaveBeenCalled();
  });

  it('remembers where the user was heading so login can return them there', async () => {
    renderRoutes({ initialEntries: ['/applications?status=OFFER'] });
    expect(await screen.findByTestId('from')).toHaveTextContent(
      'redirect:/applications?status=OFFER',
    );
  });

  it('restores a session from storage by validating the token against /users/me', async () => {
    signInStoringSession();
    renderRoutes({ initialEntries: ['/applications'] });

    // While the token is being checked the guard shows a spinner instead of bouncing to /login.
    expect(screen.queryByText('Login page')).not.toBeInTheDocument();
    expect(await screen.findByText('Applications stub')).toBeInTheDocument();
    expect(userApi.me).toHaveBeenCalledTimes(1);
  });

  it('clears the session and explains what happened when the token cannot be renewed', async () => {
    signInStoringSession();
    userApi.me.mockRejectedValue(
      Object.assign(new Error('Authentication required'), { status: 401, reason: 'token_expired' }),
    );

    renderRoutes({ initialEntries: ['/applications'] });

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    await waitFor(() => expect(localStorage.getItem('jobtrack.accessToken')).toBeNull());
    expect(localStorage.getItem('jobtrack.user')).toBeNull();
  });

  it('keeps a signed-in USER away from the admin route without a 403 dead end', async () => {
    signInStoringSession({ ...USER, role: 'USER' });
    renderRoutes({ initialEntries: ['/admin'] });

    expect(await screen.findByText('Dashboard stub')).toBeInTheDocument();
    expect(screen.queryByText('Admin stub')).not.toBeInTheDocument();
  });

  it('lets an ADMIN through the admin route', async () => {
    userApi.me.mockResolvedValue({ ...USER, role: 'ADMIN' });
    signInStoringSession({ ...USER, role: 'ADMIN' });
    renderRoutes({ initialEntries: ['/admin'] });

    expect(await screen.findByText('Admin stub')).toBeInTheDocument();
  });
});
