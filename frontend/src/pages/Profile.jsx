import { useEffect, useState } from 'react';
import { userApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { Field } from '../components/ApplicationForm';
import { useToast } from '../components/Toast';
import { formatDateTime, initials } from '../utils/format';

const TIMEZONES = [
  'Asia/Kolkata',
  'Asia/Dubai',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Australia/Sydney',
  'Europe/London',
  'Europe/Berlin',
  'Europe/Paris',
  'Africa/Johannesburg',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Sao_Paulo',
  'UTC',
];

/** Profile & settings: identity, time zone (used for every reminder), notification opt-in, password. */
export default function Profile() {
  const { user, applyProfile } = useAuth();
  const toast = useToast();

  const [profile, setProfile] = useState({
    name: user?.name || '',
    timezone: user?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    emailRemindersEnabled: user?.emailRemindersEnabled ?? false,
  });
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState(null);

  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [passwordErrors, setPasswordErrors] = useState({});
  const [savingPassword, setSavingPassword] = useState(false);

  useEffect(() => {
    if (user) {
      setProfile({
        name: user.name || '',
        timezone: user.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
        emailRemindersEnabled: user.emailRemindersEnabled ?? false,
      });
    }
  }, [user]);

  async function handleProfileSubmit(event) {
    event.preventDefault();
    setSavingProfile(true);
    setProfileError(null);
    try {
      const updated = await userApi.updateMe(profile);
      applyProfile(updated);
      toast.success('Profile updated');
    } catch (error) {
      setProfileError(error.message || 'Could not update your profile');
    } finally {
      setSavingProfile(false);
    }
  }

  async function handlePasswordSubmit(event) {
    event.preventDefault();
    const errors = {};
    if (!passwords.currentPassword) errors.currentPassword = 'Enter your current password';
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(passwords.newPassword)) {
      errors.newPassword = '8-72 characters with at least one letter and one digit';
    }
    if (passwords.confirm !== passwords.newPassword) errors.confirm = 'Passwords do not match';
    setPasswordErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSavingPassword(true);
    try {
      await userApi.changePassword({
        currentPassword: passwords.currentPassword,
        newPassword: passwords.newPassword,
      });
      setPasswords({ currentPassword: '', newPassword: '', confirm: '' });
      toast.success('Password changed. All other sessions were signed out.');
    } catch (error) {
      setPasswordErrors({ form: error.message || 'Could not change your password' });
    } finally {
      setSavingPassword(false);
    }
  }

  return (
    <div className="stack">
      <header className="page-header">
        <div>
          <h2 className="page-header__title">Profile &amp; settings</h2>
          <p className="page-header__subtitle">Your account, time zone and password.</p>
        </div>
      </header>

      <div className="grid-2">
        <section className="card">
          <header className="card__header">
            <div className="profile-head">
              <span className="avatar" aria-hidden="true">{initials(user?.name)}</span>
              <div>
                <h2 className="card__title">{user?.name}</h2>
                <p className="card__subtitle">
                  {user?.email} · {user?.role === 'ADMIN' ? 'Administrator' : 'Standard user'}
                </p>
              </div>
            </div>
          </header>
          <dl className="detail-list">
            <div className="detail-list__item">
              <dt>Member since</dt>
              <dd>{user?.createdAt ? formatDateTime(user.createdAt) : '—'}</dd>
            </div>
            <div className="detail-list__item">
              <dt>Time zone used for reminders</dt>
              <dd>{profile.timezone}</dd>
            </div>
            <div className="detail-list__item">
              <dt>Access token lifetime</dt>
              <dd>30 minutes (refreshed silently)</dd>
            </div>
          </dl>

          <form className="form form--stacked" onSubmit={handleProfileSubmit}>
            <h3 className="section-heading">Update profile</h3>
            {profileError ? (
              <p className="alert alert--error" role="alert">{profileError}</p>
            ) : null}
            <Field
              id="profile-name"
              label="Full name"
              value={profile.name}
              onChange={(value) => setProfile({ ...profile, name: value })}
            />
            <div className="field">
              <label className="field__label" htmlFor="profile-timezone">Time zone</label>
              <select
                id="profile-timezone"
                className="input"
                value={profile.timezone}
                onChange={(event) => setProfile({ ...profile, timezone: event.target.value })}
              >
                {!TIMEZONES.includes(profile.timezone) ? (
                  <option value={profile.timezone}>{profile.timezone}</option>
                ) : null}
                {TIMEZONES.map((zone) => (
                  <option key={zone} value={zone}>
                    {zone}
                  </option>
                ))}
              </select>
              <p className="field__hint">
                Interview times, reminders and the &ldquo;overdue&rdquo; calculation all use this zone.
              </p>
            </div>
            <label className="switch">
              <input
                type="checkbox"
                checked={profile.emailRemindersEnabled}
                onChange={(event) => setProfile({ ...profile, emailRemindersEnabled: event.target.checked })}
              />
              <span>
                Email me daily reminders
                <small className="switch__hint">
                  Requires an SMTP server to be configured; otherwise reminders stay in-app.
                </small>
              </span>
            </label>
            <button type="submit" className="btn btn--primary" disabled={savingProfile}>
              {savingProfile ? 'Saving…' : 'Save profile'}
            </button>
          </form>
        </section>

        <section className="card">
          <header className="card__header">
            <div>
              <h2 className="card__title">Change password</h2>
              <p className="card__subtitle">
                Changing your password revokes every stored refresh token, so other devices are signed out.
              </p>
            </div>
          </header>
          <form className="form form--stacked" onSubmit={handlePasswordSubmit} noValidate>
            {passwordErrors.form ? (
              <p className="alert alert--error" role="alert">{passwordErrors.form}</p>
            ) : null}
            <Field
              id="current-password"
              label="Current password"
              type="password"
              autoComplete="current-password"
              value={passwords.currentPassword}
              onChange={(value) => setPasswords({ ...passwords, currentPassword: value })}
              error={passwordErrors.currentPassword}
            />
            <Field
              id="new-password"
              label="New password"
              type="password"
              autoComplete="new-password"
              value={passwords.newPassword}
              onChange={(value) => setPasswords({ ...passwords, newPassword: value })}
              error={passwordErrors.newPassword}
              hint="8-72 characters, at least one letter and one digit."
            />
            <Field
              id="confirm-password"
              label="Confirm new password"
              type="password"
              autoComplete="new-password"
              value={passwords.confirm}
              onChange={(value) => setPasswords({ ...passwords, confirm: value })}
              error={passwordErrors.confirm}
            />
            <button type="submit" className="btn btn--primary" disabled={savingPassword}>
              {savingPassword ? 'Updating…' : 'Update password'}
            </button>
          </form>

          <div className="card__footer-note">
            <h3 className="section-heading">How your data is scoped</h3>
            <p className="muted-text">
              Every query is filtered by your user id on the server, and the id is taken from the signed
              JWT - never from the request body. Asking for another user&apos;s application returns 404, not
              their data.
            </p>
          </div>
        </section>
      </div>
    </div>
  );
}
