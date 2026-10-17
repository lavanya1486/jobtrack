import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import AuthLayout from '../components/AuthLayout';
import { useToast } from '../components/Toast';
import { fieldErrorMap } from '../utils/format';

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

export default function Register() {
  const { register } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function validate() {
    const next = {};
    if (form.name.trim().length < 2) next.name = 'Enter your full name';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email address';
    if (!PASSWORD_RULE.test(form.password)) {
      next.password = '8-72 characters with at least one letter and one digit';
    }
    if (form.confirm !== form.password) next.confirm = 'Passwords do not match';
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
      const user = await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
      });
      toast.success(`Account created - welcome, ${user.name.split(' ')[0]}!`);
      navigate('/dashboard', { replace: true });
    } catch (error) {
      setErrors(fieldErrorMap(error.fieldErrors));
      setFormError(error.message || 'Could not create your account');
    } finally {
      setSubmitting(false);
    }
  }

  const strength = passwordStrength(form.password);

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Your data is private: every record is scoped to your user on the server."
      footer={
        <p>
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      }
    >
      {formError ? (
        <p className="alert alert--error" role="alert">{formError}</p>
      ) : null}

      <form className="form" onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label className="field__label" htmlFor="register-name">Full name</label>
          <input
            id="register-name"
            className={`input${errors.name ? ' input--invalid' : ''}`}
            value={form.name}
            autoComplete="name"
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            aria-invalid={errors.name ? 'true' : undefined}
          />
          {errors.name ? <p className="field__error" role="alert">{errors.name}</p> : null}
        </div>

        <div className="field">
          <label className="field__label" htmlFor="register-email">Email</label>
          <input
            id="register-email"
            type="email"
            className={`input${errors.email ? ' input--invalid' : ''}`}
            value={form.email}
            autoComplete="email"
            onChange={(event) => setForm({ ...form, email: event.target.value })}
            aria-invalid={errors.email ? 'true' : undefined}
          />
          {errors.email ? <p className="field__error" role="alert">{errors.email}</p> : null}
        </div>

        <div className="field">
          <label className="field__label" htmlFor="register-password">Password</label>
          <input
            id="register-password"
            type="password"
            className={`input${errors.password ? ' input--invalid' : ''}`}
            value={form.password}
            autoComplete="new-password"
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            aria-invalid={errors.password ? 'true' : undefined}
            aria-describedby="register-password-hint"
          />
          <div className="password-meter" aria-hidden="true">
            <span className={`password-meter__bar password-meter__bar--${strength.level}`}
              style={{ width: `${strength.percent}%` }} />
          </div>
          <p className="field__hint" id="register-password-hint">
            {strength.label}
          </p>
          {errors.password ? <p className="field__error" role="alert">{errors.password}</p> : null}
        </div>

        <div className="field">
          <label className="field__label" htmlFor="register-confirm">Confirm password</label>
          <input
            id="register-confirm"
            type="password"
            className={`input${errors.confirm ? ' input--invalid' : ''}`}
            value={form.confirm}
            autoComplete="new-password"
            onChange={(event) => setForm({ ...form, confirm: event.target.value })}
            aria-invalid={errors.confirm ? 'true' : undefined}
          />
          {errors.confirm ? <p className="field__error" role="alert">{errors.confirm}</p> : null}
        </div>

        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>
    </AuthLayout>
  );
}

export function passwordStrength(password) {
  if (!password) return { level: 'empty', percent: 0, label: 'At least 8 characters, one letter and one digit' };
  let score = 0;
  if (password.length >= 8) score += 1;
  if (password.length >= 12) score += 1;
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score += 1;
  if (/\d/.test(password)) score += 1;
  if (/[^A-Za-z0-9]/.test(password)) score += 1;

  const labels = ['Very weak', 'Weak', 'Fair', 'Good', 'Strong', 'Excellent'];
  const levels = ['weak', 'weak', 'fair', 'good', 'strong', 'strong'];
  return { level: levels[score], percent: (score / 5) * 100, label: labels[score] };
}
