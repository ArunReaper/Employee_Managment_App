// Thin client for the Spring Boot API (see ../API_CONTRACT.md). Every non-2xx response carries
// {"message": "..."}; that text is what the user sees.

export const NETWORK_ERROR = "We couldn't reach the server. Please check your connection and try again.";
export const TIMEOUT_ERROR = 'The server is taking too long to respond. Please try again.';
export const UNEXPECTED_ERROR = 'Something went wrong. Please try again.';
const TIMEOUT_MS = 15000;

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(method, path, body) {
  let response;
  try {
    response = await fetch(path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (e) {
    throw new ApiError(0, e?.name === 'TimeoutError' ? TIMEOUT_ERROR : NETWORK_ERROR);
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    // a proxy or gateway error has no JSON message: the API itself is unreachable
    const fallback = [502, 503, 504].includes(response.status) ? NETWORK_ERROR : UNEXPECTED_ERROR;
    throw new ApiError(response.status, data?.message || fallback);
  }
  if (data === null) {
    throw new ApiError(response.status, UNEXPECTED_ERROR);
  }
  return data;
}

export const api = {
  listEmployees: () => request('GET', '/api/employees'),
  addEmployee: (employee) => request('POST', '/api/employees', employee),
  editEmployee: (id, change) => request('PATCH', `/api/employees/${encodeURIComponent(id)}`, change),
  deleteEmployee: (id, name) =>
    request('DELETE', `/api/employees/${encodeURIComponent(id)}?name=${encodeURIComponent(name)}`),
  signUp: (admin) => request('POST', '/api/auth/signup', admin),
  logIn: (credentials) => request('POST', '/api/auth/login', credentials),
  logOut: () => request('POST', '/api/auth/logout'),
  currentAdmin: () => request('GET', '/api/auth/me'),
};
