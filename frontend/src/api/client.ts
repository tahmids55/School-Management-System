import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

/** Shape of the problem+json body sent by the backend's GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  requestId?: string;
  errors?: Record<string, string>;
}

/** Every feature's API file uses this one instance. */
export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true, // sends the HttpOnly refresh cookie
});

// ---- Access token: kept in memory only, never in localStorage (XSS could read that). ----

let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

// ---- Refresh: one shared request, retry once, otherwise end the session. ----

interface RefreshResult {
  accessToken: string;
}

let refreshInFlight: Promise<RefreshResult> | null = null;
let onSessionEnded: () => void = () => {};

/** AuthContext registers what to do when the session cannot be renewed. */
export function setSessionEndedHandler(handler: () => void): void {
  onSessionEnded = handler;
}

/**
 * POST /auth/refresh with the cookie. Parallel callers share the same request,
 * so five failing queries cause one refresh, not five.
 */
export function refreshAccessToken<T extends RefreshResult>(): Promise<T> {
  if (!refreshInFlight) {
    refreshInFlight = api
      .post<T>('/auth/refresh')
      .then((response) => {
        setAccessToken(response.data.accessToken);
        return response.data;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight as Promise<T>;
}

// A 401 from these means "wrong password" or "no session", so refreshing would not help.
const AUTH_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout'];

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const original = error.config as RetriableConfig | undefined;
  const isAuthCall = AUTH_PATHS.some((path) => original?.url?.startsWith(path));
  if (error.response?.status !== 401 || !original || original._retried || isAuthCall) {
    return Promise.reject(error);
  }
  original._retried = true;
  try {
    await refreshAccessToken();
  } catch {
    setAccessToken(null);
    onSessionEnded();
    return Promise.reject(error);
  }
  return api(original);
});

// ---- Helpers for screens ----

export const OFFLINE_MESSAGE = 'Cannot reach the server. Check your connection.';

/** One readable sentence for any error: problem detail, plus field errors when present. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) return OFFLINE_MESSAGE;
    const problem = error.response.data as ProblemDetail | undefined;
    const main = problem?.detail || problem?.title || error.message;
    const fields = Object.values(problem?.errors ?? {});
    return fields.length ? `${main}: ${fields.join('; ')}` : main;
  }
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

/** Field errors in the shape AntD Form.setFields expects. */
export function fieldErrors(error: unknown): { name: string; errors: string[] }[] {
  if (!axios.isAxiosError(error)) return [];
  const problem = error.response?.data as ProblemDetail | undefined;
  return Object.entries(problem?.errors ?? {}).map(([name, message]) => ({ name, errors: [message] }));
}

/** Sent as the Idempotency-Key header so a double-click cannot create two payments. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
