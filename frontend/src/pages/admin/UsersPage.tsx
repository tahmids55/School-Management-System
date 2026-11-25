import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Dropdown, Empty, Input, Select, Space, Table, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  KeyOutlined,
  MoreOutlined,
  SearchOutlined,
  StopOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { errorMessage } from '../../api/client';
import {
  listUsers,
  resetUserPassword,
  unlockUser,
  updateUserStatus,
  type UserResponse,
} from '../../api/users';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader, StatusTag, showTemporaryPassword } from '../../components/common';
import { feedback } from '../../utils/feedback';
import { formatDateTime } from '../../utils/format';

const { Text } = Typography;

const PAGE_SIZE = 20;

const ROLE_OPTIONS: { value: Role | ''; label: string }[] = [
  { value: '', label: 'All roles' },
  { value: 'ADMIN', label: 'Admin' },
  { value: 'TEACHER', label: 'Teacher' },
  { value: 'STUDENT', label: 'Student' },
];

export default function UsersPage() {
  const { user: currentUser } = useAuth();
  const queryClient = useQueryClient();

  const [queryInput, setQueryInput] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState<Role | ''>('');
  const [page, setPage] = useState(0);

  const usersQuery = useQuery({
    queryKey: ['users', appliedQuery, roleFilter, page, PAGE_SIZE],
    queryFn: () =>
      listUsers({
        q: appliedQuery || undefined,
        role: roleFilter || undefined,
        page,
        size: PAGE_SIZE,
      }),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => updateUserStatus(id, enabled),
    onSuccess: (updated) => {
      feedback.message.success(updated.enabled ? 'Account enabled' : 'Account disabled');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (err) => {
      feedback.message.error(errorMessage(err));
    },
  });

  const unlockMutation = useMutation({
    mutationFn: (id: number) => unlockUser(id),
    onSuccess: () => {
      feedback.message.success('Account unlocked');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (err) => {
      feedback.message.error(errorMessage(err));
    },
  });

  const resetMutation = useMutation({
    mutationFn: (id: number) => resetUserPassword(id),
    onSuccess: (result) => {
      showTemporaryPassword(result.username, result.temporaryPassword);
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (err) => {
      feedback.message.error(errorMessage(err));
    },
  });

  const applySearch = (nextQuery = queryInput) => {
    setAppliedQuery(nextQuery.trim());
    setPage(0);
  };

  const confirmDisable = (target: UserResponse) => {
    feedback.modal.confirm({
      title: 'Disable login',
      content: `Disable login for ${target.username}? All active sessions will stop immediately.`,
      okText: 'Disable login',
      okButtonProps: { danger: true },
      onOk: () => statusMutation.mutateAsync({ id: target.id, enabled: false }),
    });
  };

  const confirmResetPassword = (target: UserResponse) => {
    feedback.modal.confirm({
      title: 'Reset password',
      content: `Generate a new temporary password for ${target.username}? Active sessions will be signed out.`,
      okText: 'Reset password',
      okButtonProps: { danger: true },
      onOk: () => resetMutation.mutateAsync(target.id),
    });
  };

  const buildActionMenu = (record: UserResponse): MenuProps => {
    const isSelf = currentUser?.id === record.id;
    const items: MenuProps['items'] = [
      {
        key: 'reset',
        label: 'Reset password',
        icon: <KeyOutlined />,
        onClick: () => confirmResetPassword(record),
      },
    ];

    if (record.locked) {
      items.push({
        key: 'unlock',
        label: 'Unlock',
        icon: <UnlockOutlined />,
        onClick: () => unlockMutation.mutate(record.id),
      });
    }

    if (record.enabled) {
      items.push({
        key: 'disable',
        label: 'Disable login',
        icon: <StopOutlined />,
        danger: true,
        disabled: isSelf,
        onClick: () => confirmDisable(record),
      });
    } else {
      items.push({
        key: 'enable',
        label: 'Enable login',
        icon: <CheckCircleOutlined />,
        onClick: () => statusMutation.mutate({ id: record.id, enabled: true }),
      });
    }

    return { items };
  };

  const columns: ColumnsType<UserResponse> = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: 'Name',
      dataIndex: 'fullName',
      key: 'fullName',
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      width: 110,
      render: (role: Role) => <StatusTag value={role} />,
    },
    {
      title: 'State',
      key: 'state',
      width: 210,
      render: (_, record) => (
        <Space wrap size={[4, 4]}>
          {record.enabled ? (
            <Tag color="green" bordered={false}>
              Enabled
            </Tag>
          ) : (
            <Tag color="red" bordered={false}>
              Disabled
            </Tag>
          )}
          {record.locked && (
            <Tag color="orange" bordered={false}>
              Locked
            </Tag>
          )}
          {record.mustChangePassword && (
            <Tag color="gold" bordered={false}>
              Temp password
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'Last login',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      width: 170,
      render: (value?: string) => (
        <Text style={{ whiteSpace: 'nowrap' }}>{formatDateTime(value)}</Text>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 90,
      align: 'right',
      render: (_, record) => (
        <Dropdown menu={buildActionMenu(record)} trigger={['click']}>
          <Button size="small" icon={<MoreOutlined />} aria-label={`Actions for ${record.username}`} />
        </Dropdown>
      ),
    },
  ];

  const rows = usersQuery.data?.content ?? [];
  const total = usersQuery.data?.totalElements ?? 0;

  return (
    <div>
      <PageHeader
        title="User accounts"
        subtitle="Enable, disable, unlock and reset logins"
      />

      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]} style={{ width: '100%' }}>
          <Input
            placeholder="Username, name or email"
            aria-label="Username, name or email"
            value={queryInput}
            allowClear
            style={{ width: 260, maxWidth: '100%' }}
            onChange={(e) => {
              const value = e.target.value;
              setQueryInput(value);
              if (value === '') applySearch('');
            }}
            onPressEnter={() => applySearch()}
          />

          <Select<Role | ''>
            value={roleFilter}
            options={ROLE_OPTIONS}
            placeholder="All roles"
            aria-label="All roles"
            style={{ width: 160 }}
            onChange={(value) => {
              setRoleFilter(value);
              setPage(0);
            }}
          />

          <Button type="primary" icon={<SearchOutlined />} onClick={() => applySearch()}>
            Search
          </Button>
        </Space>
      </Card>

      {usersQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(usersQuery.error)}
          action={
            <Button size="small" onClick={() => usersQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          <Table<UserResponse>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={usersQuery.isLoading || usersQuery.isFetching}
            scroll={{ x: 860 }}
            locale={{
              emptyText: <Empty description="No user accounts found" />,
            }}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total,
              showSizeChanger: false,
              onChange: (nextPage) => setPage(nextPage - 1),
            }}
          />
        </Card>
      )}
    </div>
  );
}
