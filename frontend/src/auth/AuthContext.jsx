import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi, userApi } from '../api/endpoints';
import { setSessionExpiredHandler } from '../api/client';
import {
  clearSession,
  getAccessToken,
  getRefreshToken,
  getStoredUser,
  setSession,
  setStoredUser,
} from './tokenStorage';

const AuthContext = createContext(null);

/**
 * Owns the session: who is logged in, and how the app signs in / out / recovers.
 *
 * On mount, a stored access token is verified against `GET /users/me`. If it has expired the axios
 * interceptor refreshes it transparently; if the refresh also fails (revoked family, deleted
 * account) the session is cleared and the user lands on the login screen with an explanation -
 * never on a page full of 401s.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => getStoredUser());
  const [bootstrapping, setBootstrapping] = useState(() => Boolean(getAccessToken()));
  const [sessionNotice, setSessionNotice] = useState(null);

  const handleSessionExpired = useCallback((message) => {
    clearSession();
    setUser(null);
    setSessionNotice(message || 'Your session expired. Please sign in again.');
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(handleSessionExpired);
    return () => setSessionExpiredHandler(null);
  }, [handleSessionExpired]);

  useEffect(() => {
    let cancelled = false;

    async function verifySession() {
      if (!getAccessToken()) {
        setBootstrapping(false);
        return;
      }
      try {
        const profile = await userApi.me();
        if (!cancelled) {
          setUser(profile);
          setStoredUser(profile);
        }
      } catch {
        // The interceptor already tried to refresh; if we are here the session is gone.
        if (!cancelled) handleSessionExpired('Your session expired. Please sign in again.');
      } finally {
        if (!cancelled) setBootstrapping(false);
      }
    }

    verifySession();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (credentials) => {
    const session = await authApi.login(credentials);
    setSession(session);
    setUser(session.user);
    setSessionNotice(null);
    return session.user;
  }, []);

  const register = useCallback(async (payload) => {
    const session = await authApi.register(payload);
    setSession(session);
    setUser(session.user);
    setSessionNotice(null);
    return session.user;
  }, []);

  const logout = useCallback(async () => {
    const hadSession = Boolean(getStoredUser());
    try {
      const refreshToken = getRefreshToken();
      if (refreshToken) await authApi.logout(refreshToken);
    } catch {
      // Logging out must succeed locally even if the server call fails.
    }
    clearSession();
    setUser(null);
    setSessionNotice(hadSession ? 'You have been signed out.' : null);
  }, []);

  const refreshProfile = useCallback(async () => {
    const profile = await userApi.me();
    setUser(profile);
    setStoredUser(profile);
    return profile;
  }, []);

  const applyProfile = useCallback((profile) => {
    setUser(profile);
    setStoredUser(profile);
  }, []);

  const dismissNotice = useCallback(() => setSessionNotice(null), []);

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: Boolean(user),
      isAdmin: user?.role === 'ADMIN',
      bootstrapping,
      sessionNotice,
      dismissNotice,
      login,
      register,
      logout,
      refreshProfile,
      applyProfile,
    }),
    [
      user,
      bootstrapping,
      sessionNotice,
      dismissNotice,
      login,
      register,
      logout,
      refreshProfile,
      applyProfile,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
