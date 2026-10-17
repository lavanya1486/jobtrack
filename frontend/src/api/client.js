import axios from 'axios';
import { getAccessToken, getRefreshToken, setSession, clearSession } from '../auth/tokenStorage';

/**
 * Single axios instance for the whole app.
 *
 * Two interceptors implement the session behaviour:
 *  1. request  - attach `Authorization: Bearer <accessToken>`;
 *  2. response - on 401 with reason `token_expired`, refresh once and replay the request.
 *
 * Concurrent requests that all fail with an expired token share ONE refresh call: the others wait
 * on the same promise instead of each hitting `/auth/refresh` (which would trip refresh-token reuse
 * detection and log the user out).
 */
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

const apiClient = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
  // Spring binds repeated keys (status=A&status=B) to a List; axios' default adds [].
  paramsSerializer: { indexes: null },
});

let refreshPromise = null;
let onSessionExpired = null;

/** Lets the auth context react (redirect to login) when the session cannot be renewed. */
export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config || {};
    const status = error.response?.status;
    const reason = error.response?.data?.details?.reason;
    const isAuthCall = /\/auth\/(login|register|refresh)/.test(original.url || '');

    const canRetry =
      status === 401 &&
      !original._retried &&
      !isAuthCall &&
      (reason === 'token_expired' || reason === 'missing_credentials' || reason === 'token_invalid') &&
      Boolean(getRefreshToken());

    if (!canRetry) {
      return Promise.reject(normalise(error));
    }

    original._retried = true;
    try {
      await refreshSession();
      return await apiClient(original);
    } catch (refreshError) {
      clearSession();
      if (onSessionExpired) {
        onSessionExpired('Your session expired. Please sign in again.');
      }
      return Promise.reject(normalise(refreshError));
    }
  },
);

/** Runs a single refresh at a time and stores the rotated token pair. */
export async function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        throw new Error('No refresh token available');
      }
      // Plain axios: bypasses the interceptors so a failing refresh cannot recurse.
      const { data } = await axios.post(
        `${baseURL}/auth/refresh`,
        { refreshToken },
        { headers: { 'Content-Type': 'application/json' } },
      );
      setSession(data);
      return data;
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

/** Turns an axios error into a small, UI-friendly shape. */
export function normalise(error) {
  const response = error?.response;
  const data = response?.data;
  const normalised = new Error(data?.message || error?.message || 'Request failed');
  normalised.status = response?.status;
  normalised.code = data?.details?.code || data?.error;
  normalised.reason = data?.details?.reason;
  normalised.fieldErrors = data?.fieldErrors || [];
  normalised.details = data?.details || {};
  normalised.response = response;
  return normalised;
}

export default apiClient;
