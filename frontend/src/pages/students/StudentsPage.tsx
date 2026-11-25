import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Empty, Input, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownloadOutlined, PlusOutlined } from '@ant-design/icons';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import {
  exportStudents,
  listStudents,
  STUDENT_STATUSES,
  studentKeys,
  type StudentStatus,
  type StudentSummary,
} from '../../api/students';
import { PageHeader, StatusTag } from '../../components/common';
import { useClasses } from '../../hooks/useClasses';
import { feedback } from '../../utils/feedback';
import { humanize } from '../../utils/format';
import StudentFormDrawer from './StudentFormDrawer';

const { Text } = Typography;

const PAGE_SIZE = 20;

/** /students: search + class/status filters, a server-paged table, CSV export and "Admit student". */
export default function StudentsPage() {
  const navigate = useNavigate();
  const isAdmin = useAuth().hasRole('ADMIN');
  const [searchText, setSearchText] = useState('');
  const [q, setQ] = useState('');
  const [classId, setClassId] = useState<number>();
  const [status, setStatus] = useState<StudentStatus>();
  const [page, setPage] = useState(0);
  const [admitting, setAdmitting] = useState(false);

  const classes = useClasses();
  const search = { q: q || undefined, classId, status, page, size: PAGE_SIZE };
  const students = useQuery({
    queryKey: studentKeys.list(search),
    queryFn: () => listStudents(search),
    // Keep the old page on screen while the next one loads (no flicker).
    placeholderData: keepPreviousData,
  });

  const exportCsv = useMutation({
    mutationFn: () => exportStudents({ classId, status }),
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  // Any filter change starts again at the first page.
  function applySearch(value: string) {
    setQ(value.trim());
    setPage(0);
  }

  const columns: ColumnsType<StudentSummary> = [
    { title: 'Admission no.', dataIndex: 'admissionNo', render: (value: string) => <Text strong>{value}</Text> },
    { title: 'Name', dataIndex: 'fullName' },
    {
      title: 'Class',
      dataIndex: 'className',
      render: (name?: string | null) => name || <Text type="secondary">Unassigned</Text>,
    },
    { title: 'Roll', dataIndex: 'rollNo', align: 'right', render: (roll?: number | null) => roll ?? '-' },
    { title: 'Gender', dataIndex: 'gender', render: (gender: string) => humanize(gender) },
    { title: 'Guardian phone', dataIndex: 'guardianPhone' },
    { title: 'Status', dataIndex: 'status', render: (value: StudentStatus) => <StatusTag value={value} /> },
  ];

  const classOptions = (classes.data ?? []).map((c) => ({ value: c.id, label: c.displayName }));
  const statusOptions = STUDENT_STATUSES.map((value) => ({ value, label: humanize(value) }));
  const filtered = Boolean(q || classId || status);

  return (
    <div>
      <PageHeader
        title="Students"
        subtitle={isAdmin ? undefined : 'Students of your classes'}
        actions={
          // Export and admitting are admin-only on the server too; teachers do not see the buttons.
          isAdmin && (
            <>
              <Button icon={<DownloadOutlined />} loading={exportCsv.isPending} onClick={() => exportCsv.mutate()}>
                Export CSV
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setAdmitting(true)}>
                Admit student
              </Button>
            </>
          )
        }
      />

      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]} style={{ width: '100%' }}>
          <Input.Search
            placeholder="Name, admission no. or guardian phone"
            aria-label="Name, admission no. or guardian phone"
            allowClear
            value={searchText}
            style={{ width: 320, maxWidth: '100%' }}
            onChange={(event) => {
              setSearchText(event.target.value);
              if (event.target.value === '') applySearch('');
            }}
            onSearch={applySearch}
          />
          <Select
            placeholder="All classes"
            aria-label="All classes"
            allowClear
            loading={classes.isLoading}
            options={classOptions}
            value={classId}
            style={{ width: 180, maxWidth: '100%' }}
            onChange={(value?: number) => {
              setClassId(value);
              setPage(0);
            }}
          />
          <Select
            placeholder="Any status"
            aria-label="Any status"
            allowClear
            options={statusOptions}
            value={status}
            style={{ width: 160, maxWidth: '100%' }}
            onChange={(value?: StudentStatus) => {
              setStatus(value);
              setPage(0);
            }}
          />
        </Space>
      </Card>

      {students.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(students.error)}
          action={<Button onClick={() => void students.refetch()}>Try again</Button>}
        />
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          <Table<StudentSummary>
            rowKey="id"
            columns={columns}
            dataSource={students.data?.content ?? []}
            loading={students.isFetching}
            scroll={{ x: 'max-content' }}
            locale={{
              emptyText: (
                <Empty
                  description={
                    filtered
                      ? 'No students match these filters'
                      : isAdmin
                        ? 'No students yet'
                        : 'No students in your classes yet'
                  }
                />
              ),
            }}
            onRow={(student) => ({
              onClick: () => navigate(`/students/${student.id}`),
              style: { cursor: 'pointer' },
            })}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total: students.data?.totalElements ?? 0,
              showSizeChanger: false,
              showTotal: (total) => `${total} students`,
              onChange: (next) => setPage(next - 1),
            }}
          />
        </Card>
      )}

      <StudentFormDrawer
        open={admitting}
        onClose={() => setAdmitting(false)}
        onSaved={(student) => navigate(`/students/${student.id}`)}
      />
    </div>
  );
}
