import type { Role } from './auth';
import { api } from './client';

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: Role;
  enabled: boolean;
  locked: boolean;
  mustChangePassword: boolean;
  lastLoginAt?: string;
}

export interface TemporaryPasswordResponse {
  username: string;
  temporaryPassword: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserQuery {
  q?: string;
  role?: Role;
  page?: number;
  size?: number;
}

export async function listUsers(query: UserQuery = {}): Promise<PageResponse<UserResponse>> {
  const params: Record<string, string | number> = {
    page: query.page ?? 0,
    size: query.size ?? 20,
  };
  if (query.q?.trim()) params.q = query.q.trim();
  if (query.role) params.role = query.role;

  const { data } = await api.get<PageResponse<UserResponse>>('/users', { params });
  return data;
}

export async function updateUserStatus(id: number, enabled: boolean): Promise<UserResponse> {
  const { data } = await api.patch<UserResponse>(`/users/${id}/status`, { enabled });
  return data;
}

export async function unlockUser(id: number): Promise<UserResponse> {
  const { data } = await api.post<UserResponse>(`/users/${id}/unlock`);
  return data;
}

export async function resetUserPassword(id: number): Promise<TemporaryPasswordResponse> {
  const { data } = await api.post<TemporaryPasswordResponse>(`/users/${id}/reset-password`);
  return data;
}
