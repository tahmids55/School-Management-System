import { api, refreshAccessToken } from './client';

export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT';

export interface CurrentUser {
  id: number;
  username: string;
  fullName: string;
  email: string;
  role: Role;
  mustChangePassword: boolean;
  studentId?: number | null;
  teacherId?: number | null;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  /** Access-token lifetime in seconds (field name as sent by the backend's TokenResponse). */
  expiresIn: number;
  user: CurrentUser;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** POST /auth/login (F04). */
export async function login(request: LoginRequest): Promise<TokenResponse> {
  const { data } = await api.post<TokenResponse>('/auth/login', request);
  return data;
}

/** POST /auth/change-password (F10). Ends all sessions on success. */
export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await api.post('/auth/change-password', request);
}

/** POST /auth/refresh (F09). Uses the HttpOnly cookie; shares one request with the client interceptor. */
export function refresh(): Promise<TokenResponse> {
  return refreshAccessToken<TokenResponse>();
}

/** POST /auth/logout (F09). Clears the refresh cookie on the server. */
export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}

/** GET /auth/me (F04). */
export async function me(): Promise<CurrentUser> {
  const { data } = await api.get<CurrentUser>('/auth/me');
  return data;
}
