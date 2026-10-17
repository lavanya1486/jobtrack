import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import AuthLayout from '../components/AuthLayout';
import { useToast } from '../components/Toast';
import { fieldErrorMap } from '../utils/format';

const DEMO_CREDENTIALS = { email: 'demo@jobtrack.local', password: 'Demo1234!' };

export default function Login() {
  const { login, sessionNotice, dismissNotice } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = location.state?.from || '/dashboard';

  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function validate() {
    const next = {};
    if (!form.email.trim()) next.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email address';
    if (!form.password) next.password = 'Password is required';
    return next;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const validation = validate();
    setErrors(validation);
    setFormError(null);
    if (Object.keys(validation).length > 0) return;

    setSubmitting(true);
    try {
      const user = await login({ email: form.email.trim(), password: form.password });
      toast.success(`Welcome back, ${user.name.split(' ')[0]}!`);
      dismissNotice();
      navigate(redirectTo, { replace: true });
    } catch (error) {
      setErrors(fieldErrorMap(error.fieldErrors));
      setFormError(error.message || 'Could not sign you in');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Track applications, interviews and offers in one dashboard."
      footer={
        <p>
          No account yet? <Link to="/register">Create one</Link> — it takes ten seconds.
        </p>
      }
    >
      {sessionNotice ? (
        <p className="alert alert--info" role="status">
          {sessionNotice}
        </p>
      ) : null}

      {formError ? (
        <p className="alert alert--error" role="alert">
          {formError}
        </p>
      ) : null}

      <form className="form" onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label className="field__label" htmlFor="login-email">Email</label>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="email"
            className={`input${errors.email ? ' input--invalid' : ''}`}
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
            aria-invalid={errors.email ? 'true' : undefined}
            aria-describedby={errors.email ? 'login-email-error' : undefined}
          />
          {errors.email ? (
            <p className="field__error" id="login-email-error" role="alert">{errors.email}</p>
          ) : null}
        </div>

        <div className="field">
          <label className="field__label" htmlFor="login-password">Password</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            className={`input${errors.password ? ' input--invalid' : ''}`}
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            aria-invalid={errors.password ? 'true' : undefined}
            aria-describedby={errors.password ? 'login-password-error' : undefined}
          />
          {errors.password ? (
            <p className="field__error" id="login-password-error" role="alert">{errors.password}</p>
          ) : null}
        </div>

        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <div className="auth__demo">
        <p>
          <strong>Try the seeded demo</strong> — 24 applications, 21 interview rounds and real
          analytics.
        </p>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => setForm(DEMO_CREDENTIALS)}
        >
          Fill demo credentials
        </button>
      </div>
    </AuthLayout>
  );
}
