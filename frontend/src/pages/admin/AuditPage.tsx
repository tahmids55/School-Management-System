import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, AutoComplete, Button, Card, Empty, Input, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined } from '@ant-design/icons';
import { listAuditLogs, type AuditLogResponse } from '../../api/audit';
import { errorMessage } from '../../api/client';
import { PageHeader } from '../../components/common';
import { formatDateTime } from '../../utils/format';

const { Text } = Typography;

const PAGE_SIZE = 25;

const KNOWN_ACTIONS = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'LOGOUT',
  'ACCOUNT_LOCKED',
  'ACCOUNT_CREATED',
  'PASSWORD_CHANGED',
  'PASSWORD_RESET',
  'USER_UPDATED',
  'NOTICE_PUBLISHED',
  'NOTICE_DELETED',
  'MARKS_SAVED',
  'EXAM_PUBLISHED',
  'INVOICE_CREATED',
  'PAYMENT_RECORDED',
].map((value) => ({ value }));

export default function AuditPage() {
  const [actorInput, setActorInput] = useState('');
  const [actionInput, setActionInput] = useState('');
  const [appliedActor, setAppliedActor] = useState('');
  const [appliedAction, setAppliedAction] = useState('');
  const [page, setPage] = useState(0);

  const auditQuery = useQuery({
    queryKey: ['audit-logs', appliedActor, appliedAction, page, PAGE_SIZE],
    queryFn: () =>
      listAuditLogs({
        actor: appliedActor || undefined,
        action: appliedAction || undefined,
        page,
        size: PAGE_SIZE,
      }),
  });

  const applyFilters = (nextActor = actorInput, nextAction = actionInput) => {
    setAppliedActor(nextActor.trim());
    setAppliedAction(nextAction.trim());
    setPage(0);
  };

  const columns: ColumnsType<AuditLogResponse> = [
    {
      title: 'When',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (value: string) => <Text style={{ whiteSpace: 'nowrap' }}>{formatDateTime(value)}</Text>,
    },
    {
      title: 'User',
      dataIndex: 'actor',
      key: 'actor',
      width: 150,
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: 'Action',
      dataIndex: 'action',
      key: 'action',
      width: 180,
      render: (value: string) => (
        <Tag color="blue" bordered={false}>
          {value}
        </Tag>
      ),
    },
    {
      title: 'Record',
      key: 'record',
      width: 170,
      render: (_, row) =>
        row.entityId ? `${row.entityType} #${row.entityId}` : row.entityType,
    },
    {
      title: 'Details',
      dataIndex: 'details',
      key: 'details',
      render: (value?: string) => (
        <Text style={{ wordBreak: 'break-word' }}>{value || '-'}</Text>
      ),
    },
  ];

  const rows = auditQuery.data?.content ?? [];
  const total = auditQuery.data?.totalElements ?? 0;

  return (
    <div>
      <PageHeader
        title="Audit log"
        subtitle="Who did what, and when. Entries cannot be edited."
      />

      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]} style={{ width: '100%' }}>
          <Input
            placeholder="Username"
            aria-label="Username"
            value={actorInput}
            allowClear
            style={{ width: 220, maxWidth: '100%' }}
            onChange={(e) => {
              const value = e.target.value;
              setActorInput(value);
              if (value === '') applyFilters('', actionInput);
            }}
            onPressEnter={() => applyFilters()}
          />

          <AutoComplete
            options={KNOWN_ACTIONS}
            value={actionInput}
            allowClear
            placeholder="Any action"
            aria-label="Any action"
            style={{ width: 240, maxWidth: '100%' }}
            filterOption={(inputValue, option) =>
              (option?.value ?? '').toUpperCase().includes(inputValue.trim().toUpperCase())
            }
            onChange={(value) => {
              setActionInput(value);
              if (!value) applyFilters(actorInput, '');
            }}
            onSelect={(value) => {
              setActionInput(value);
              applyFilters(actorInput, value);
            }}
          />

          <Button type="primary" icon={<SearchOutlined />} onClick={() => applyFilters()}>
            Filter
          </Button>
        </Space>
      </Card>

      {auditQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(auditQuery.error)}
          action={
            <Button size="small" onClick={() => auditQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          <Table<AuditLogResponse>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={auditQuery.isLoading || auditQuery.isFetching}
            scroll={{ x: 760 }}
            locale={{
              emptyText: <Empty description="No audit entries found" />,
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
