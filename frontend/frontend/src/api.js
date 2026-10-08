// All calls to the backend go through this file.
// In development, Vite forwards /api/... to http://localhost:8080 (see vite.config.js).

const TOKEN_KEY = 'plm_token';
const USER_KEY = 'plm_user';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY));
  } catch {
    return null;
  }
}

export function saveSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function request(path, { method = 'GET', body, raw = false } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error('Cannot reach the server. Is the backend running?');
  }

  // An expired or invalid token: send the user back to the login page
  if (res.status === 401 && token) {
    clearSession();
    window.location.href = '/login';
    throw new Error('Your session expired. Please log in again.');
  }

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const data = await res.json();
      if (data.errors) message = Object.values(data.errors).join(', ');
      else if (data.message) message = data.message;
    } catch {
      // the server sent no JSON body
    }
    if (res.status === 403) message = 'You do not have permission to do this.';
    throw new Error(message);
  }

  if (raw) return res;
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  login: (email, password) => request('/api/auth/login', { method: 'POST', body: { email, password } }),

  parts: (q) => request('/api/parts' + (q ? `?q=${encodeURIComponent(q)}` : '')),
  part: (id) => request(`/api/parts/${id}`),
  createPart: (body) => request('/api/parts', { method: 'POST', body }),
  versions: (id) => request(`/api/parts/${id}/versions`),

  submit: (id, versionId, reviewerEmails) =>
    request(`/api/parts/${id}/versions/${versionId}/submit`, { method: 'POST', body: { reviewerEmails } }),
  release: (id, versionId) =>
    request(`/api/parts/${id}/versions/${versionId}/transition`, { method: 'POST', body: { targetState: 'RELEASED' } }),
  revise: (id) => request(`/api/parts/${id}/revise`, { method: 'POST' }),
  tasksForVersion: (id, versionId) => request(`/api/parts/${id}/versions/${versionId}/tasks`),

  bom: (id) => request(`/api/parts/${id}/bom`),
  addBom: (id, body) => request(`/api/parts/${id}/bom`, { method: 'POST', body }),
  removeBom: (id, linkId) => request(`/api/parts/${id}/bom/${linkId}`, { method: 'DELETE' }),
  whereUsed: (id) => request(`/api/parts/${id}/where-used`),

  inbox: () => request('/api/tasks'),
  decide: (taskId, decision, comment) =>
    request(`/api/tasks/${taskId}/decision`, { method: 'POST', body: { decision, comment } }),

  users: () => request('/api/users'),
  createUser: (body) => request('/api/users', { method: 'POST', body }),
  audit: () => request('/api/audit'),
};

// The Excel file needs the login token, so it is fetched with fetch() and then saved
export async function downloadBomExcel(id, partNumber) {
  const res = await request(`/api/parts/${id}/bom/export`, { raw: true });
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `bom-${partNumber}.xlsx`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '';
}
