import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Badge, Button, Empty, List, Popover, Spin, Typography } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { errorMessage } from '../api/client';
import {
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  notificationKeys,
  type NotificationItem,
} from '../api/notifications';
import { colors } from '../theme';
import { formatDateTime } from '../utils/format';
import { feedback } from '../utils/feedback';

const { Text } = Typography;
const POLL_MS = 60_000;

/** Header bell: unread badge (checked every minute), the newest 15 notifications in a dropdown. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const unread = useQuery({ queryKey: notificationKeys.unreadCount, queryFn: getUnreadCount, refetchInterval: POLL_MS });
  // The list is only loaded while the dropdown is open.
  const list = useQuery({ queryKey: notificationKeys.list, queryFn: () => listNotifications(), enabled: open });

  const refresh = () => queryClient.invalidateQueries({ queryKey: notificationKeys.all });
  const readOne = useMutation({ mutationFn: markNotificationRead, onSettled: refresh });
  const readAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: refresh,
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  function openNotification(item: NotificationItem) {
    if (!item.read) readOne.mutate(item.id);
    setOpen(false);
    if (item.link) navigate(item.link);
  }

  const count = unread.data ?? 0;
  const items = list.data?.content ?? [];

  const content = (
    <div style={{ width: 'min(360px, calc(100vw - 32px))' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Text strong>Notifications</Text>
        <Button type="link" size="small" disabled={count === 0} loading={readAll.isPending}
          onClick={() => readAll.mutate()}>
          Mark all read
        </Button>
      </div>
      {list.isError ? (
        <Alert type="error" showIcon message={errorMessage(list.error)}
          action={<Button size="small" onClick={() => void list.refetch()}>Try again</Button>} />
      ) : list.isLoading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      ) : items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="You're all caught up" />
      ) : (
        <List
          dataSource={items}
          rowKey="id"
          style={{ maxHeight: 400, overflowY: 'auto' }}
          renderItem={(item) => (
            <List.Item
              onClick={() => openNotification(item)}
              style={{
                cursor: 'pointer',
                paddingInline: 8,
                borderRadius: 8,
                background: item.read ? undefined : colors.surface,
              }}
            >
              <List.Item.Meta
                title={<Text strong={!item.read}>{item.title}</Text>}
                description={
                  <>
                    <div style={{ color: colors.charcoal }}>{item.message}</div>
                    <Text type="secondary" style={{ fontSize: 12 }}>{formatDateTime(item.createdAt)}</Text>
                  </>
                }
              />
            </List.Item>
          )}
        />
      )}
    </div>
  );

  return (
    <Popover content={content} trigger="click" placement="bottomRight" open={open} onOpenChange={setOpen} arrow={false}>
      <Badge count={count} size="small" overflowCount={99}>
        <Button type="text" shape="circle" icon={<BellOutlined style={{ fontSize: 18 }} />}
          aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'} />
      </Badge>
    </Popover>
  );
}
