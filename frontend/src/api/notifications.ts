import { api } from './client';
import type { PageResponse } from './notices';

// F26: the signed-in user's own in-app notifications.

export interface NotificationItem {
  id: number;
  title: string;
  message: string;
  /** App path to open, e.g. "/my/fees". Missing when there is nothing to open. */
  link?: string;
  read: boolean;
  createdAt: string;
}

export async function listNotifications(page = 0, size = 15): Promise<PageResponse<NotificationItem>> {
  const { data } = await api.get<PageResponse<NotificationItem>>('/notifications', { params: { page, size } });
  return data;
}

export async function getUnreadCount(): Promise<number> {
  const { data } = await api.get<{ count: number }>('/notifications/unread-count');
  return data.count;
}

export async function markNotificationRead(id: number): Promise<void> {
  await api.post(`/notifications/${id}/read`);
}

export async function markAllNotificationsRead(): Promise<void> {
  await api.post('/notifications/read-all');
}

/** Everything lives under ['notifications'], so invalidating notificationKeys.all refreshes the bell. */
export const notificationKeys = {
  all: ['notifications'] as const,
  list: ['notifications', 'list'] as const,
  unreadCount: ['notifications', 'unread-count'] as const,
};
