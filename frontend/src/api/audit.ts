import { api } from './client';

export interface AuditLogResponse {
  id: number;
  actor: string;
  action: string;
  entityType: string;
  entityId?: string;
  details?: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditLogQuery {
  actor?: string;
  action?: string;
  page?: number;
  size?: number;
}

export async function listAuditLogs(query: AuditLogQuery = {}): Promise<PageResponse<AuditLogResponse>> {
  const params: Record<string, string | number> = {
    page: query.page ?? 0,
    size: query.size ?? 25,
  };
  if (query.actor?.trim()) params.actor = query.actor.trim();
  if (query.action?.trim()) params.action = query.action.trim();

  const { data } = await api.get<PageResponse<AuditLogResponse>>('/audit-logs', { params });
  return data;
}
