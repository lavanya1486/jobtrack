/**
 * Token storage.
 *
 * Trade-off worth explaining: tokens live in `localStorage`, which is readable by any script on the
 * origin. The mitigations are a strict CSP-friendly bundle with no third-party scripts, short-lived
 * access tokens (30 min) and rotating, server-side revocable refresh tokens. A hardened deployment
 * would keep the refresh token in an httpOnly cookie instead - the API already supports that shape.
 */
const ACCESS_KEY = 'jobtrack.accessToken';
const REFRESH_KEY = 'jobtrack.refreshToken';
const USER_KEY = 'jobtrack.user';

const storage = typeof window !== 'undefined' ? window.localStorage : undefined;

function safeGet(key) {
  try {
    return storage ? storage.getItem(key) : null;
  } catch {
    return null;
  }
}

function safeSet(key, value) {
  try {
    if (storage) storage.setItem(key, value);
  } catch {
    /* storage disabled (private mode) - the session simply will not survive a reload */
  }
}

function safeRemove(key) {
  try {
    if (storage) storage.removeItem(key);
  } catch {
    /* ignore */
  }
}

export function getAccessToken() {
  return safeGet(ACCESS_KEY);
}

export function getRefreshToken() {
  return safeGet(REFRESH_KEY);
}

export function getStoredUser() {
  const raw = safeGet(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function setSession(authResponse) {
  if (!authResponse) return;
  if (authResponse.accessToken) safeSet(ACCESS_KEY, authResponse.accessToken);
  if (authResponse.refreshToken) safeSet(REFRESH_KEY, authResponse.refreshToken);
  if (authResponse.user) safeSet(USER_KEY, JSON.stringify(authResponse.user));
}

export function setStoredUser(user) {
  if (user) safeSet(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  safeRemove(ACCESS_KEY);
  safeRemove(REFRESH_KEY);
  safeRemove(USER_KEY);
}
