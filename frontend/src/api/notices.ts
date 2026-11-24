import { api } from './client';

export type NoticeAudience = 'ALL' | 'TEACHERS' | 'STUDENTS';

export interface NoticeRequest {
  title: string;
  body: string;
  audience: NoticeAudience;
  pinned: boolean;
}

export interface NoticeResponse {
  id: number;
  title: string;
  body: string;
  audience: NoticeAudience;
  pinned: boolean;
  publishedAt: string;
  author?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function listNotices(page = 0, size = 10): Promise<PageResponse<NoticeResponse>> {
  const { data } = await api.get<PageResponse<NoticeResponse>>('/notices', {
    params: { page, size },
  });
  return data;
}

export async function getNotice(id: number): Promise<NoticeResponse> {
  const { data } = await api.get<NoticeResponse>(`/notices/${id}`);
  return data;
}

export async function createNotice(request: NoticeRequest): Promise<NoticeResponse> {
  const { data } = await api.post<NoticeResponse>('/notices', request);
  return data;
}

export async function updateNotice(id: number, request: NoticeRequest): Promise<NoticeResponse> {
  const { data } = await api.put<NoticeResponse>(`/notices/${id}`, request);
  return data;
}

export async function deleteNotice(id: number): Promise<void> {
  await api.delete(`/notices/${id}`);
}
