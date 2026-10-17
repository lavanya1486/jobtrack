import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../api/endpoints';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../components/Toast';
import Login from '../pages/Login';

vi.mock('../api/endpoints', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), logout: vi.fn() },
  userApi: { me: vi.fn(), updateMe: vi.fn(), changePassword: vi.fn() },
}));

const SESSION = {
  accessToken: 'access.jwt.token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  expiresIn: 1800,
  user: { id: 1, name: 'Asha Verma', email: 'asha@example.com', role: 'USER' },
};

function renderLogin() {
  return render(
    <ToastProvider>
      <AuthProvider>
        <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }} initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/dashboard" element={<h1>Dashboard loaded</h1>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </ToastProvider>,
  );
}

describe('login page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders the credentials form with labelled inputs', () => {
    renderLogin();
    expect(screen.getByLabelText(/^email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('blocks an empty submission and never calls the API', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(authApi.login).not.toHaveBeenCalled();
  });

  it('rejects a malformed email address client-side', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/^email/i), 'not-an-email');
    await user.type(screen.getByLabelText(/^password/i), 'Secret123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(authApi.login).not.toHaveBeenCalled();
  });

  it('signs in, stores the session and redirects to the dashboard', async () => {
    authApi.login.mockResolvedValue(SESSION);
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/^email/i), '  asha@example.com  ');
    await user.type(screen.getByLabelText(/^password/i), 'Secret123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await screen.findByText('Dashboard loaded');
    expect(authApi.login).toHaveBeenCalledWith({
      email: 'asha@example.com',
      password: 'Secret123',
    });
    expect(localStorage.getItem('jobtrack.accessToken')).toBe('access.jwt.token');
    expect(JSON.parse(localStorage.getItem('jobtrack.user')).name).toBe('Asha Verma');
  });

  it('surfaces the server message when credentials are rejected', async () => {
    authApi.login.mockRejectedValue(
      Object.assign(new Error('Invalid email or password'), { status: 401, fieldErrors: [] }),
    );
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/^email/i), 'asha@example.com');
    await user.type(screen.getByLabelText(/^password/i), 'WrongPass1');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    await waitFor(() => expect(localStorage.getItem('jobtrack.accessToken')).toBeNull());
  });

  it('maps server field errors onto the matching input', async () => {
    authApi.login.mockRejectedValue(
      Object.assign(new Error('Request validation failed'), {
        status: 400,
        fieldErrors: [{ field: 'email', message: 'Email must be a valid address' }],
      }),
    );
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/^email/i), 'asha@example.com');
    await user.type(screen.getByLabelText(/^password/i), 'Secret123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    const email = await screen.findByLabelText(/^email/i);
    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Email must be a valid address')).toBeInTheDocument();
  });

  it('fills the demo credentials when asked', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.click(screen.getByRole('button', { name: /fill demo credentials/i }));

    expect(screen.getByLabelText(/^email/i)).toHaveValue('demo@jobtrack.local');
    expect(screen.getByLabelText(/^password/i)).toHaveValue('Demo1234!');
  });
});
