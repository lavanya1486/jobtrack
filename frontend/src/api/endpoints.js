import apiClient from './client';

/** Drops empty values so the query string stays readable and Spring does not bind "". */
export function cleanParams(params = {}) {
  const clean = {};
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) {
      const items = value.filter((item) => item !== undefined && item !== null && item !== '');
      if (items.length > 0) clean[key] = items;
      return;
    }
    clean[key] = value;
  });
  return clean;
}

const unwrap = (promise) => promise.then((response) => response.data);

export const authApi = {
  register: (payload) => unwrap(apiClient.post('/auth/register', payload)),
  login: (payload) => unwrap(apiClient.post('/auth/login', payload)),
  logout: (refreshToken) => unwrap(apiClient.post('/auth/logout', { refreshToken })),
};

export const userApi = {
  me: () => unwrap(apiClient.get('/users/me')),
  updateMe: (payload) => unwrap(apiClient.patch('/users/me', payload)),
  changePassword: (payload) => unwrap(apiClient.post('/users/me/password', payload)),
};

export const applicationsApi = {
  list: (params) => unwrap(apiClient.get('/applications', { params: cleanParams(params) })),
  get: (id) => unwrap(apiClient.get(`/applications/${id}`)),
  create: (payload) => unwrap(apiClient.post('/applications', payload)),
  update: (id, payload) => unwrap(apiClient.put(`/applications/${id}`, payload)),
  changeStatus: (id, payload) => unwrap(apiClient.patch(`/applications/${id}/status`, payload)),
  remove: (id) => apiClient.delete(`/applications/${id}`),
  history: (id) => unwrap(apiClient.get(`/applications/${id}/status-history`)),
  filterOptions: () => unwrap(apiClient.get('/applications/filter-options')),

  interviews: (id) => unwrap(apiClient.get(`/applications/${id}/interviews`)),
  addInterview: (id, payload) => unwrap(apiClient.post(`/applications/${id}/interviews`, payload)),
};

export const interviewsApi = {
  list: (params) => unwrap(apiClient.get('/interviews', { params: cleanParams(params) })),
  upcoming: (limit = 5) => unwrap(apiClient.get('/interviews/upcoming', { params: { limit } })),
  update: (id, payload) => unwrap(apiClient.put(`/interviews/${id}`, payload)),
  remove: (id) => apiClient.delete(`/interviews/${id}`),
};

export const remindersApi = {
  list: (params) => unwrap(apiClient.get('/reminders', { params: cleanParams(params) })),
  upcoming: (params) => unwrap(apiClient.get('/reminders/upcoming', { params: cleanParams(params) })),
  create: (payload) => unwrap(apiClient.post('/reminders', payload)),
  update: (id, payload) => unwrap(apiClient.put(`/reminders/${id}`, payload)),
  complete: (id) => unwrap(apiClient.patch(`/reminders/${id}/complete`)),
  reopen: (id) => unwrap(apiClient.patch(`/reminders/${id}/reopen`)),
  remove: (id) => apiClient.delete(`/reminders/${id}`),
};

export const analyticsApi = {
  summary: () => unwrap(apiClient.get('/analytics/summary')),
  trends: (months = 6) => unwrap(apiClient.get('/analytics/trends', { params: { months } })),
  upcoming: (params) => unwrap(apiClient.get('/analytics/upcoming', { params: cleanParams(params) })),
};

/**
 * ADMIN-only endpoints. `users` returns a plain list (the backend does not paginate it); search and
 * paging are therefore done in the admin page, which also keeps the UI responsive while typing.
 */
export const adminApi = {
  users: () => unwrap(apiClient.get('/admin/users')),
  stats: () => unwrap(apiClient.get('/admin/stats')),
  changeRole: (id, role) => unwrap(apiClient.patch(`/admin/users/${id}/role`, { role })),
  removeUser: (id) => apiClient.delete(`/admin/users/${id}`),
};
